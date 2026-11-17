package com.instabazaar.review.repository;

import static com.instabazaar.catalog.repository.CatalogReadRepository.*;

import com.instabazaar.review.model.Review;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.repository.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {
  private final EntityAccess jpa;
  private final SqlAccess sql;

  public ReviewRepository(EntityAccess jpa, SqlAccess sql) {
    this.jpa = jpa;
    this.sql = sql;
  }

  public Review get(UUID id) {
    return jpa.find(Review.class, id);
  }

  public Review lock(UUID id) {
    return jpa.lock(Review.class, id);
  }

  public boolean exists(UUID user, UUID product) {
    return sql.count(
            "select count(*) from review where reviewer_id=:user and product_id=:product",
            Map.of("user", user, "product", product))
        > 0;
  }

  public List<Contracts.Review> list(String where, Map<String, ?> args, int offset, int size) {
    var params = new HashMap<String, Object>(args);
    params.put("offset", offset);
    params.put("limit", size);
    return sql
        .rows(
            "select r.*,u.name reviewer_name from review r join app_user u on u.id=r.reviewer_id"
                + " where "
                + where
                + " order by r.created_at desc,r.id asc limit :limit offset :offset",
            params)
        .stream()
        .map(
            r ->
                new Contracts.Review(
                    uuid(r, "id"),
                    uuid(r, "product_id"),
                    new Contracts.Reviewer(uuid(r, "reviewer_id"), str(r, "reviewer_name")),
                    number(r, "rating").intValue(),
                    str(r, "review_text"),
                    str(r, "status"),
                    number(r, "version").longValue(),
                    instant(r, "created_at"),
                    instant(r, "updated_at")))
        .toList();
  }

  public long count(String where, Map<String, ?> args) {
    return sql.count("select count(*) from review r where " + where, args);
  }
}
