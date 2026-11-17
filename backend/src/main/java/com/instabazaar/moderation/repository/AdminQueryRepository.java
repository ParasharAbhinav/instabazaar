package com.instabazaar.moderation.repository;

import static com.instabazaar.catalog.repository.CatalogReadRepository.*;

import com.instabazaar.catalog.repository.CatalogReadRepository;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.repository.SqlAccess;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class AdminQueryRepository {
  private final SqlAccess sql;

  public AdminQueryRepository(SqlAccess sql) {
    this.sql = sql;
  }

  public long count(String table, String where, Map<String, ?> args) {
    if (!Set.of("app_user", "report").contains(table)) throw new IllegalArgumentException();
    return sql.count("select count(*) from " + table + " where " + where, args);
  }

  private Map<String, Object> page(Map<String, ?> args, int offset, int size) {
    var p = new HashMap<String, Object>(args);
    p.put("offset", offset);
    p.put("limit", size);
    return p;
  }

  public List<User> users(String where, Map<String, ?> args, int offset, int size) {
    return sql
        .rows(
            "select id,name,email,role,enabled,version,created_at from app_user where "
                + where
                + " order by created_at desc,id asc limit :limit offset :offset",
            page(args, offset, size))
        .stream()
        .map(
            r ->
                new User(
                    uuid(r, "id"),
                    str(r, "name"),
                    str(r, "email"),
                    str(r, "role"),
                    bool(r, "enabled"),
                    number(r, "version").longValue(),
                    instant(r, "created_at")))
        .toList();
  }

  public List<Report> reports(String where, Map<String, ?> args, int offset, int size) {
    return sql
        .rows(
            "select * from report where "
                + where
                + " order by created_at desc,id asc limit :limit offset :offset",
            page(args, offset, size))
        .stream()
        .map(
            r ->
                new Report(
                    uuid(r, "id"),
                    r.get("product_id") == null ? "REVIEW" : "PRODUCT",
                    uuid(r, r.get("product_id") == null ? "review_id" : "product_id"),
                    str(r, "reason"),
                    str(r, "details"),
                    str(r, "status"),
                    str(r, "resolution_note"),
                    instant(r, "resolved_at"),
                    number(r, "version").longValue(),
                    instant(r, "created_at")))
        .toList();
  }

  public Statistics statistics() {
    var r =
        sql.rows(
                """
                select (select count(*) from app_user where enabled) enabled_users,
                (select count(*) from seller_profile) seller_stores,
                (select count(*)
                """
                    + CatalogReadRepository.JOINS
                    + "where "
                    + CatalogReadRepository.PUBLIC
                    + """
                    ) public_products,
                    (select count(*) from review where status='PENDING') pending_reviews,
                    (select count(*) from report where status='OPEN') open_reports
                    """,
                Map.of())
            .getFirst();
    return new Statistics(
        number(r, "enabled_users").longValue(),
        number(r, "seller_stores").longValue(),
        number(r, "public_products").longValue(),
        number(r, "pending_reviews").longValue(),
        number(r, "open_reports").longValue());
  }
}
