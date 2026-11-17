package com.instabazaar.service;

import com.instabazaar.dto.SearchQuery;
import com.instabazaar.dto.Views;
import com.instabazaar.entity.Product;
import com.instabazaar.entity.ProductTag;
import com.instabazaar.enums.ListingStatus;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.ProductRepository;
import com.instabazaar.util.Text;
import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true)
public class SearchService {
    private static final Set<String> STOP = Set.of("a", "an", "the", "for", "with", "from", "that", "i", "saw", "in", "on", "reel", "reels", "post", "of", "and", "to", "my", "is", "it");
    private final ProductRepository products;
    private final ViewMapper mapper;

    public SearchService(ProductRepository products, ViewMapper mapper) { this.products = products; this.mapper = mapper; }

    public Views.SearchResults search(@Valid SearchQuery filters) {
        if (filters.minPrice() != null && filters.maxPrice() != null && filters.minPrice().compareTo(filters.maxPrice()) > 0) {
            throw DomainException.invalid("Minimum price must not exceed maximum price.");
        }
        List<String> tokens = Arrays.stream(Text.normalized(filters.q()).split(" +"))
                .filter(token -> !token.isBlank() && !STOP.contains(token)).map(token -> switch (token) {
                    case "dresses" -> "dress"; case "kurtis" -> "kurti"; case "tops" -> "top"; case "sarees" -> "saree"; default -> token;
                }).distinct().limit(16).toList();
        Specification<Product> specification = (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(builder.equal(root.get("status"), ListingStatus.ACTIVE));
            conditions.add(builder.isFalse(root.get("hidden")));
            conditions.add(builder.isTrue(root.get("seller").get("user").get("active")));
            if (filters.categoryId() != null) conditions.add(builder.equal(root.get("category").get("id"), filters.categoryId()));
            if (filters.sellerId() != null) conditions.add(builder.equal(root.get("seller").get("id"), filters.sellerId()));
            if (filters.minPrice() != null) conditions.add(builder.greaterThanOrEqualTo(root.get("price"), filters.minPrice()));
            if (filters.maxPrice() != null) conditions.add(builder.lessThanOrEqualTo(root.get("price"), filters.maxPrice()));
            if (!filters.colour().isBlank()) conditions.add(like(builder, root.get("colour"), filters.colour()));
            if (!filters.size().isBlank()) {
                Expression<String> normalizedSizes = builder.concat(builder.concat(",", builder.function("replace", String.class, builder.lower(root.get("sizes")), builder.literal(" "), builder.literal(""))), ",");
                conditions.add(builder.like(normalizedSizes, "%," + filters.size().replace(" ", "").toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + ",%", '\\'));
            }
            if (filters.available() != null) conditions.add(filters.available() ? builder.greaterThan(root.get("stock"), 0) : builder.equal(root.get("stock"), 0));
            Expression<Integer> score = builder.literal(0);
            for (String token : tokens) {
                Predicate title = like(builder, root.get("title"), token);
                var subquery = query.subquery(Long.class);
                Root<ProductTag> tag = subquery.from(ProductTag.class);
                subquery.select(tag.get("id")).where(builder.equal(tag.get("product"), root), like(builder, tag.get("label"), token));
                Predicate tags = builder.exists(subquery);
                Predicate attributes = builder.or(like(builder, root.get("category").get("name"), token),
                        like(builder, root.get("colour"), token), like(builder, root.get("sizes"), token),
                        like(builder, root.get("material"), token), like(builder, root.get("occasion"), token), like(builder, root.get("brand"), token));
                Predicate seller = like(builder, root.get("seller").get("storeName"), token);
                conditions.add(builder.or(title, tags, attributes, seller, like(builder, root.get("description"), token)));
                score = builder.sum(score, builder.<Integer>selectCase().when(title, 10).when(tags, 7).when(attributes, 5).when(seller, 4).otherwise(1));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Order primary = switch (filters.sort()) {
                    case "price-asc" -> builder.asc(root.get("price"));
                    case "price-desc" -> builder.desc(root.get("price"));
                    case "newest" -> builder.desc(root.get("createdAt"));
                    default -> tokens.isEmpty() ? builder.desc(root.get("createdAt")) : builder.desc(score);
                };
                query.orderBy(primary, builder.desc(root.get("createdAt")), builder.desc(root.get("id")));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
        var result = products.findAll(specification, PageRequest.of(filters.page(), 12));
        return new Views.SearchResults(result.getContent().stream().map(mapper::product).toList(), result.getTotalElements(), result.getNumber(), result.getTotalPages());
    }

    private Predicate like(CriteriaBuilder builder, Expression<String> field, String term) {
        String escaped = term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return builder.like(builder.lower(field), "%" + escaped + "%", '\\');
    }
}
