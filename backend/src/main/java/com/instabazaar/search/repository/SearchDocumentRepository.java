package com.instabazaar.search.repository;

import com.instabazaar.shared.repository.SqlAccess;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SearchDocumentRepository {
  private final SqlAccess sql;

  public SearchDocumentRepository(SqlAccess sql) {
    this.sql = sql;
  }

  private static final String REFRESH =
      """
      update product p set search_document =
        setweight(to_tsvector('simple',p.title),'A') ||
        setweight(to_tsvector('simple',coalesce((select string_agg(t.tag,' ' order by t.normalized_tag) from product_tag t where t.product_id=p.id and t.status='APPROVED'),'')),'B') ||
        setweight(to_tsvector('simple',concat_ws(' ',c.name,p.colour,p.material,p.occasion,p.brand,
          (select string_agg(z.size_code,' ' order by z.size_code) from product_size z where z.product_id=p.id))),'C') ||
        setweight(to_tsvector('simple',concat_ws(' ',p.description,s.store_name)),'D')
      from category c,seller_profile s where c.id=p.category_id and s.id=p.seller_id and
      """;

  public void product(UUID id) {
    sql.update(REFRESH + "p.id=:id", Map.of("id", id));
  }

  public void store(UUID id) {
    sql.update(REFRESH + "p.seller_id=:id", Map.of("id", id));
  }

  public void category(UUID id) {
    sql.update(REFRESH + "p.category_id=:id", Map.of("id", id));
  }
}
