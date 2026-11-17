package com.instabazaar.media.repository;

import com.instabazaar.media.model.*;
import com.instabazaar.shared.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {
  private final EntityAccess jpa;
  private final SqlAccess sql;

  public MediaRepository(EntityAccess jpa, SqlAccess sql) {
    this.jpa = jpa;
    this.sql = sql;
  }

  public MediaAsset get(UUID id) {
    return jpa.find(MediaAsset.class, id);
  }

  public MediaAsset lock(UUID id) {
    return jpa.lock(MediaAsset.class, id);
  }

  public boolean referenced(UUID id) {
    return sql.count(
            "select (select count(*) from product_image where asset_id=:id)+(select count(*) from"
                + " seller_profile where logo_asset_id=:id)",
            Map.of("id", id))
        > 0;
  }

  public boolean publicAttachment(UUID id) {
    return sql.count(
            """
            select (select count(*) from product_image i join product p on p.id=i.product_id
            join seller_profile s on s.id=p.seller_id join app_user u on u.id=s.user_id join category c on c.id=p.category_id
            where i.asset_id=:id and p.listing_status='ACTIVE' and p.moderation_status='VISIBLE' and u.enabled and c.active)
            +(select count(*) from seller_profile s join app_user u on u.id=s.user_id where s.logo_asset_id=:id and u.enabled)
            """,
            Map.of("id", id))
        > 0;
  }

  public boolean imageUsedElsewhere(UUID asset, UUID product) {
    var args = new HashMap<String, Object>();
    args.put("asset", asset);
    args.put("product", product);
    return sql.count(
            "select count(*) from product_image where asset_id=:asset and (:product::uuid is null"
                + " or product_id<>:product)",
            args)
        > 0;
  }

  public List<UUID> cleanupCandidates(Instant before, int limit) {
    return sql
        .rows(
            """
            select m.id from media_asset m where (m.state='DELETING' or m.created_at<:before)
            and not exists(select 1 from product_image i where i.asset_id=m.id)
            and not exists(select 1 from seller_profile s where s.logo_asset_id=m.id)
            order by m.created_at,m.id limit :limit
            """,
            Map.of("before", java.sql.Timestamp.from(before), "limit", limit))
        .stream()
        .map(r -> (UUID) r.get("id"))
        .toList();
  }

  public boolean keyExists(String key) {
    return sql.count("select count(*) from media_asset where storage_key=:key", Map.of("key", key))
        > 0;
  }
}
