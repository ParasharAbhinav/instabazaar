package com.instabazaar.storefront.repository;

import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.storefront.model.SellerProfile;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class SellerProfileRepository {
  private final EntityAccess jpa;

  public SellerProfileRepository(EntityAccess jpa) {
    this.jpa = jpa;
  }

  public Optional<SellerProfile> byUser(UUID userId) {
    return jpa
        .query(
            "select s from SellerProfile s join fetch s.user where s.user.id=:id",
            SellerProfile.class,
            Map.of("id", userId))
        .stream()
        .findFirst();
  }

  public SellerProfile get(UUID id) {
    return jpa.find(SellerProfile.class, id);
  }

  public SellerProfile lock(UUID id) {
    return jpa.lock(SellerProfile.class, id);
  }
}
