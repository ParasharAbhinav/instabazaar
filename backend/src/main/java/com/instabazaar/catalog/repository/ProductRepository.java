package com.instabazaar.catalog.repository;

import com.instabazaar.catalog.model.*;
import com.instabazaar.media.model.ProductImage;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
  private final EntityAccess jpa;
  private final SqlAccess sql;

  public ProductRepository(EntityAccess jpa, SqlAccess sql) {
    this.jpa = jpa;
    this.sql = sql;
  }

  public Product get(UUID id) {
    return jpa.find(Product.class, id);
  }

  public Product owned(UUID id, UUID userId, boolean lock) {
    com.instabazaar.shared.validation.Rules.required(id, "productId");
    var rows =
        jpa.query(
            "select p from Product p where p.id=:id and p.seller.user.id=:user",
            Product.class,
            Map.of("id", id, "user", userId));
    if (rows.isEmpty()) throw BusinessException.missing();
    return lock ? jpa.lock(Product.class, id) : rows.getFirst();
  }

  public Product lock(UUID id) {
    return jpa.lock(Product.class, id);
  }

  public List<ProductSize> sizes(UUID id) {
    return jpa.query(
        "select s from ProductSize s where s.product.id=:id", ProductSize.class, Map.of("id", id));
  }

  public List<ProductTag> tags(UUID id) {
    return jpa.query(
        "select t from ProductTag t where t.product.id=:id order by t.tag,t.id",
        ProductTag.class,
        Map.of("id", id));
  }

  public List<ProductImage> images(UUID id) {
    return jpa.query(
        "select i from ProductImage i where i.product.id=:id order by i.displayOrder",
        ProductImage.class,
        Map.of("id", id));
  }

  public String lower(String text) {
    return sql.string("select lower(btrim(:text))", Map.of("text", text));
  }

  public String upper(String text) {
    return sql.string("select upper(btrim(:text))", Map.of("text", text));
  }

  public void changed(Product product, long previous) {
    jpa.flush();
    if (product.getVersion() == previous) {
      jpa.increment(product);
      sql.update(
          "update product set updated_at=current_timestamp where id=:id",
          Map.of("id", product.getId()));
    }
    jpa.flush();
  }
}
