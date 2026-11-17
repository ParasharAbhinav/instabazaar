package com.instabazaar.wishlist.repository;

import com.instabazaar.shared.repository.SqlAccess;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistRepository {
  private final SqlAccess sql;

  public WishlistRepository(SqlAccess sql) {
    this.sql = sql;
  }

  public void save(UUID user, UUID product) {
    sql.update(
        "insert into wishlist_item(user_id,product_id) values(:user,:product) on"
            + " conflict(user_id,product_id) do nothing",
        Map.of("user", user, "product", product));
  }

  public void remove(UUID user, UUID product) {
    sql.update(
        "delete from wishlist_item where user_id=:user and product_id=:product",
        Map.of("user", user, "product", product));
  }

  public List<Map<String, Object>> list(UUID user, int offset, int size) {
    return sql.rows(
        "select product_id,created_at from wishlist_item where user_id=:user order by created_at"
            + " desc,product_id asc limit :limit offset :offset",
        Map.of("user", user, "limit", size, "offset", offset));
  }

  public long count(UUID user) {
    return sql.count(
        "select count(*) from wishlist_item where user_id=:user", Map.of("user", user));
  }
}
