package com.instabazaar.search.service;

import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.search.dto.SearchCriteria;
import com.instabazaar.search.repository.ProductSearchRepository;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SearchService {
  private final ProductSearchRepository repository;
  private final CatalogReadService catalog;

  public SearchService(ProductSearchRepository repository, CatalogReadService catalog) {
    this.repository = repository;
    this.catalog = catalog;
  }

  public Page<ProductSummary> search(SearchCriteria c) {
    Rules.required(c, "criteria");
    Rules.offset(c.page(), c.size());
    try {
      String q = Rules.text(c.q(), "q", 200, true, false);
      if (q != null) q = q.replaceAll("(?U)\\s+", " ").strip();
      String colour = c.colour() == null ? null : Rules.name(c.colour(), "colour", 40);
      String size = c.sizeCode() == null ? null : Rules.name(c.sizeCode(), "sizeCode", 20);
      var min = c.minPrice() == null ? null : Rules.money(c.minPrice(), "minPrice");
      var max = c.maxPrice() == null ? null : Rules.money(c.maxPrice(), "maxPrice");
      if (min != null && max != null && min.compareTo(max) > 0)
        throw BusinessException.invalid("minPrice", "Must not exceed maxPrice.");
      String sort =
          c.sort() == null ? (q == null || q.isBlank() ? "newest" : "relevance") : c.sort();
      if (!Set.of("newest", "price_asc", "price_desc", "relevance").contains(sort))
        throw BusinessException.invalid("sort", "Unsupported sort.");
      var normalized =
          new SearchCriteria(
              q,
              c.categoryId(),
              c.sellerId(),
              min,
              max,
              colour,
              size,
              c.available(),
              sort,
              c.page(),
              c.size());
      var selected = repository.select(normalized);
      var rows = catalog.publicSummaries(selected.ids());
      return Rules.page(
          selected.ids().stream().filter(rows::containsKey).map(rows::get).toList(),
          c.page(),
          c.size(),
          selected.total());
    } catch (BusinessException ex) {
      if (ex.getStatus() != 400) throw ex;
      throw new BusinessException("INVALID_FILTER", 400, ex.getMessage(), ex.getFieldErrors());
    }
  }
}
