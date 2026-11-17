package com.instabazaar.search.repository;

import com.instabazaar.catalog.repository.CatalogReadRepository;
import com.instabazaar.search.dto.SearchCriteria;
import com.instabazaar.shared.repository.SqlAccess;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ProductSearchRepository {
  public record Selection(List<UUID> ids, long total) {}

  private final CatalogReadRepository reads;
  private final SqlAccess sql;

  public ProductSearchRepository(CatalogReadRepository reads, SqlAccess sql) {
    this.reads = reads;
    this.sql = sql;
  }

  public Selection select(SearchCriteria c) {
    StringBuilder where = new StringBuilder(CatalogReadRepository.PUBLIC);
    var args = new HashMap<String, Object>();
    String exact = "lower(regexp_replace(btrim(p.title),'[[:space:]]+',' ','g'))=lower(:q)";
    boolean query = c.q() != null && !c.q().isBlank();
    if (query) {
      where
          .append(" and (")
          .append(exact)
          .append(" or p.search_document @@ websearch_to_tsquery('simple',:q))");
      args.put("q", c.q());
    }
    if (c.categoryId() != null) {
      where.append(" and p.category_id=:category");
      args.put("category", c.categoryId());
    }
    if (c.sellerId() != null) {
      where.append(" and p.seller_id=:seller");
      args.put("seller", c.sellerId());
    }
    if (c.minPrice() != null) {
      where.append(" and p.price>=:min");
      args.put("min", c.minPrice());
    }
    if (c.maxPrice() != null) {
      where.append(" and p.price<=:max");
      args.put("max", c.maxPrice());
    }
    if (c.colour() != null) {
      where.append(" and p.colour=lower(btrim(:colour))");
      args.put("colour", c.colour());
    }
    if (c.sizeCode() != null) {
      where.append(
          " and exists(select 1 from product_size z where z.product_id=p.id and"
              + " z.size_code=upper(btrim(:size)))");
      args.put("size", c.sizeCode());
    }
    if (c.available() != null) {
      where.append(" and (p.available and p.stock_quantity>0)=:available");
      args.put("available", c.available());
    }
    String order =
        switch (c.sort()) {
          case "price_asc" -> "p.price asc,p.id asc";
          case "price_desc" -> "p.price desc,p.id asc";
          case "relevance" ->
              query
                  ? "("
                      + exact
                      + ") desc,ts_rank_cd(p.search_document,websearch_to_tsquery('simple',:q))"
                      + " desc,p.id asc"
                  : "p.created_at desc,p.id asc";
          default -> "p.created_at desc,p.id asc";
        };
    long total = reads.count(where.toString(), args);
    return new Selection(
        reads.ids(where.toString(), args, order, c.page() * c.size(), c.size()), total);
  }
}
