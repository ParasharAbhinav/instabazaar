package com.instabazaar.wishlist.service;

import com.instabazaar.catalog.repository.CatalogReadRepository;
import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.validation.Rules;
import com.instabazaar.wishlist.repository.WishlistRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
public class WishlistService {
  private final CurrentUser current;
  private final WishlistRepository repository;
  private final CatalogReadService catalog;

  public WishlistService(
      CurrentUser current, WishlistRepository repository, CatalogReadService catalog) {
    this.current = current;
    this.repository = repository;
    this.catalog = catalog;
  }

  public void save(UUID productId) {
    var actor = current.require();
    catalog.requirePublic(productId);
    repository.save(actor.getId(), productId);
  }

  public void remove(UUID productId) {
    repository.remove(current.require().getId(), Rules.required(productId, "productId"));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SavedProduct> list(int page, int size) {
    int offset = Rules.offset(page, size);
    UUID user = current.require().getId();
    var saved = repository.list(user, offset, size);
    var products =
        catalog.publicSummaries(
            saved.stream().map(r -> CatalogReadRepository.uuid(r, "product_id")).toList());
    var items =
        saved.stream()
            .map(
                r -> {
                  UUID id = CatalogReadRepository.uuid(r, "product_id");
                  return new SavedProduct(
                      id,
                      CatalogReadRepository.instant(r, "created_at"),
                      products.containsKey(id) ? "VISIBLE" : "UNAVAILABLE",
                      products.get(id));
                })
            .toList();
    return Rules.page(items, page, size, repository.count(user));
  }
}
