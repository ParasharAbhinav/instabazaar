package com.instabazaar.storefront.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.account.repository.UserRepository;
import com.instabazaar.catalog.repository.CatalogMutationLock;
import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.media.service.MediaService;
import com.instabazaar.search.service.SearchDocumentService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts.Store;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.*;
import com.instabazaar.storefront.dto.StoreWrite;
import com.instabazaar.storefront.model.SellerProfile;
import com.instabazaar.storefront.repository.SellerProfileRepository;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StorefrontService {
  private final CurrentUser current;
  private final SellerProfileRepository stores;
  private final UserRepository users;
  private final EntityAccess jpa;
  private final MediaService media;
  private final ExternalUrlValidator urls;
  private final CatalogReadService reads;
  private final SearchDocumentService search;
  private final CatalogMutationLock mutation;
  private final Validator validator;

  public StorefrontService(
      CurrentUser current,
      SellerProfileRepository stores,
      UserRepository users,
      EntityAccess jpa,
      MediaService media,
      ExternalUrlValidator urls,
      CatalogReadService reads,
      SearchDocumentService search,
      CatalogMutationLock mutation,
      Validator validator) {
    this.current = current;
    this.stores = stores;
    this.users = users;
    this.jpa = jpa;
    this.media = media;
    this.urls = urls;
    this.reads = reads;
    this.search = search;
    this.mutation = mutation;
    this.validator = validator;
  }

  @Transactional(readOnly = true)
  public Store own() {
    return reads.store(requireForSeller().getId(), false);
  }

  @Transactional(readOnly = true)
  public Store publicStore(UUID id) {
    return reads.store(Rules.required(id, "sellerId"), true);
  }

  public SellerProfile requireForSeller() {
    return stores
        .byUser(current.require(Role.SELLER).getId())
        .orElseThrow(BusinessException::missing);
  }

  public Store replace(StoreWrite request) {
    Rules.required(request, "store");
    mutation.acquire();
    var actor = users.lock(current.require(Role.SELLER).getId());
    if (!actor.isEnabled() || actor.getRole() != Role.SELLER)
      throw new BusinessException("ACCESS_DENIED", 403, "Seller access is required.");
    String name = Rules.name(request.storeName(), "storeName", 120);
    String description = Rules.text(request.description(), "description", 2000, false, false);
    var existing = stores.byUser(actor.getId());
    SellerProfile store;
    if (existing.isEmpty()) {
      if (request.version() != null) throw BusinessException.conflict();
      store = new SellerProfile(actor, name, description);
    } else {
      store = stores.lock(existing.get().getId());
      Rules.version(request.version(), store.getVersion());
      store.setStoreName(name);
      store.setDescription(description);
    }
    store.setLogoAsset(
        request.logoAssetId() == null ? null : media.ownedReady(request.logoAssetId()));
    store.setPublicEmail(Rules.text(request.publicEmail(), "publicEmail", 254, true, false));
    store.setPublicPhone(PhoneNumberValidator.validate(request.publicPhone(), "publicPhone"));
    store.setWhatsappNumber(
        PhoneNumberValidator.validate(request.whatsappNumber(), "whatsappNumber"));
    store.setInstagramUrl(urls.validate(request.instagramUrl(), "instagramUrl"));
    store.setFacebookUrl(urls.validate(request.facebookUrl(), "facebookUrl"));
    if (!validator.validateProperty(store, "publicEmail").isEmpty())
      throw BusinessException.invalid("publicEmail", "Invalid email.");
    if (existing.isEmpty()) jpa.persist(store);
    jpa.flush();
    search.store(store.getId());
    return reads.store(store.getId(), false);
  }

  public void requireComplete(SellerProfile store) {
    if (store.getStoreName().isBlank() || store.getDescription().isBlank())
      throw BusinessException.invalid(
          "store", "Publishing requires a store name and nonblank store description.");
  }
}
