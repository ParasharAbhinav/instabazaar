package com.instabazaar.catalog.repository;

import com.instabazaar.media.service.MediaUrls;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.repository.SqlAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogReadRepository {
  public static final String JOINS =
      " from product p join seller_profile s on s.id=p.seller_id join app_user u on u.id=s.user_id"
          + " join category c on c.id=p.category_id ";
  public static final String PUBLIC =
      " p.listing_status='ACTIVE' and p.moderation_status='VISIBLE' and u.enabled and c.active ";
  private final SqlAccess sql;
  private final MediaUrls urls;

  public CatalogReadRepository(SqlAccess sql, MediaUrls urls) {
    this.sql = sql;
    this.urls = urls;
  }

  public boolean isPublic(UUID id) {
    return sql.count("select count(*)" + JOINS + "where p.id=:id and " + PUBLIC, Map.of("id", id))
        == 1;
  }

  public boolean ownsProduct(UUID id, UUID user) {
    return sql.count(
            "select count(*)" + JOINS + "where p.id=:id and s.user_id=:user",
            Map.of("id", id, "user", user))
        == 1;
  }

  public boolean ownsStore(UUID id, UUID user) {
    return sql.count(
            "select count(*) from seller_profile where id=:id and user_id=:user",
            Map.of("id", id, "user", user))
        == 1;
  }

  public List<UUID> ids(String where, Map<String, ?> args, String order, int offset, int size) {
    var params = new HashMap<String, Object>(args);
    params.put("offset", offset);
    params.put("limit", size);
    return sql
        .rows(
            "select p.id"
                + JOINS
                + "where "
                + where
                + " order by "
                + order
                + " limit :limit offset :offset",
            params)
        .stream()
        .map(r -> uuid(r, "id"))
        .toList();
  }

  public long count(String where, Map<String, ?> args) {
    return sql.count("select count(*)" + JOINS + "where " + where, args);
  }

  public Map<UUID, ManagedProduct> products(List<UUID> ids, boolean publicOnly) {
    if (ids.isEmpty()) return Map.of();
    if (ids.size() > 100) throw new IllegalArgumentException("Batch limit is 100.");
    var args = Map.of("ids", ids);
    var rows =
        sql.rows(
            """
            select p.*,s.store_name,c.name category_name,c.slug category_slug,c.description category_description
            """
                + JOINS
                + "where p.id in (:ids)"
                + (publicOnly ? " and " + PUBLIC : ""),
            args);
    var stores = stores(rows.stream().map(r -> uuid(r, "seller_id")).distinct().toList(), false);
    var ratings =
        ratings(
            "r.product_id",
            " from review r where r.product_id in (:ids) and r.status='APPROVED'",
            ids);
    var images = new HashMap<UUID, List<Image>>();
    sql.rows(
            "select * from product_image where product_id in (:ids) order by"
                + " product_id,display_order",
            args)
        .forEach(
            r ->
                images
                    .computeIfAbsent(uuid(r, "product_id"), k -> new ArrayList<>())
                    .add(
                        new Image(
                            uuid(r, "id"),
                            uuid(r, "asset_id"),
                            urls.url(uuid(r, "asset_id")),
                            number(r, "display_order").intValue(),
                            str(r, "alt_text"))));
    var sizes =
        strings(
            "select product_id,size_code value from product_size where product_id in (:ids) order"
                + " by size_code",
            args);
    var tags =
        strings(
            "select product_id,tag value from product_tag where product_id in (:ids) and"
                + " status='APPROVED' order by normalized_tag,id",
            args);
    var result = new LinkedHashMap<UUID, ManagedProduct>();
    for (var r : rows) {
      UUID id = uuid(r, "id");
      var detail =
          new ProductDetail(
              id,
              str(r, "title"),
              str(r, "description"),
              ((BigDecimal) r.get("price")).setScale(2).toPlainString(),
              str(r, "currency"),
              str(r, "colour"),
              sizes.getOrDefault(id, List.of()),
              str(r, "material"),
              str(r, "occasion"),
              str(r, "brand"),
              number(r, "stock_quantity").intValue(),
              bool(r, "available") && number(r, "stock_quantity").intValue() > 0,
              str(r, "source_url"),
              stores.get(uuid(r, "seller_id")),
              new Category(
                  uuid(r, "category_id"),
                  str(r, "category_name"),
                  str(r, "category_slug"),
                  str(r, "category_description")),
              images.getOrDefault(id, List.of()),
              tags.getOrDefault(id, List.of()),
              ratings.getOrDefault(id, new Rating(null, 0)),
              instant(r, "created_at"),
              instant(r, "updated_at"),
              number(r, "version").longValue());
      result.put(
          id,
          new ManagedProduct(
              detail, bool(r, "available"), str(r, "listing_status"), str(r, "moderation_status"),
              publicOnly ? null : str(r, "reel_caption")));
    }
    return result;
  }

  public Map<UUID, Store> stores(List<UUID> ids, boolean publicOnly) {
    if (ids.isEmpty()) return Map.of();
    var ratings =
        ratings(
            "p.seller_id",
            " from review r join product p on p.id=r.product_id join seller_profile s on"
                + " s.id=p.seller_id join app_user u on u.id=s.user_id join category c on"
                + " c.id=p.category_id where p.seller_id in (:ids) and r.status='APPROVED' and "
                + PUBLIC,
            ids);
    var result = new HashMap<UUID, Store>();
    for (var r :
        sql.rows(
            "select s.* from seller_profile s join app_user u on u.id=s.user_id where s.id in"
                + " (:ids)"
                + (publicOnly ? " and u.enabled" : ""),
            Map.of("ids", ids))) {
      UUID id = uuid(r, "id");
      result.put(
          id,
          new Store(
              id,
              str(r, "store_name"),
              str(r, "description"),
              urls.url(uuid(r, "logo_asset_id")),
              str(r, "public_email"),
              str(r, "public_phone"),
              str(r, "whatsapp_number"),
              str(r, "instagram_url"),
              str(r, "facebook_url"),
              ratings.getOrDefault(id, new Rating(null, 0)),
              instant(r, "created_at"),
              number(r, "version").longValue()));
    }
    return result;
  }

  private Map<UUID, Rating> ratings(String key, String from, List<UUID> ids) {
    var result = new HashMap<UUID, Rating>();
    for (var r :
        sql.rows(
            "select "
                + key
                + " id,round(avg(r.rating),2) average,count(*) count"
                + from
                + " group by "
                + key,
            Map.of("ids", ids)))
      result.put(
          uuid(r, "id"), new Rating((BigDecimal) r.get("average"), number(r, "count").longValue()));
    return result;
  }

  private Map<UUID, List<String>> strings(String query, Map<String, ?> args) {
    var result = new HashMap<UUID, List<String>>();
    for (var r : sql.rows(query, args))
      result.computeIfAbsent(uuid(r, "product_id"), k -> new ArrayList<>()).add(str(r, "value"));
    return result;
  }

  public static ProductSummary summary(ManagedProduct m) {
    var p = m.product();
    return new ProductSummary(
        p.id(),
        p.title(),
        p.price(),
        p.currency(),
        p.available(),
        p.images().isEmpty() ? null : p.images().getFirst().url(),
        new SellerSummary(p.seller().id(), p.seller().storeName()),
        p.category(),
        p.rating());
  }

  public static String str(Map<String, Object> r, String key) {
    return (String) r.get(key);
  }

  public static UUID uuid(Map<String, Object> r, String key) {
    return (UUID) r.get(key);
  }

  public static Number number(Map<String, Object> r, String key) {
    return (Number) r.get(key);
  }

  public static boolean bool(Map<String, Object> r, String key) {
    return Boolean.TRUE.equals(r.get(key));
  }

  public static Instant instant(Map<String, Object> r, String key) {
    Object value = r.get(key);
    return value == null
        ? null
        : value instanceof java.sql.Timestamp t
            ? t.toInstant()
            : ((java.time.OffsetDateTime) value).toInstant();
  }
}
