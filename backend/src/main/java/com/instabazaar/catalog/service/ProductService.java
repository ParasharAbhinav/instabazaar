package com.instabazaar.catalog.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.dto.ProductWrite;
import com.instabazaar.catalog.model.*;
import com.instabazaar.catalog.repository.*;
import com.instabazaar.media.model.*;
import com.instabazaar.media.service.MediaService;
import com.instabazaar.search.service.SearchDocumentService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.*;
import com.instabazaar.storefront.service.StorefrontService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {
  private final CurrentUser current;
  private final ProductRepository products;
  private final EntityAccess jpa;
  private final StorefrontService stores;
  private final CategoryService categories;
  private final MediaService media;
  private final SearchDocumentService search;
  private final CatalogReadService reads;
  private final ExternalUrlValidator urls;
  private final CatalogMutationLock mutation;
  private final CatalogReadRepository listings;

  public ProductService(
      CurrentUser current,
      ProductRepository products,
      EntityAccess jpa,
      StorefrontService stores,
      CategoryService categories,
      MediaService media,
      SearchDocumentService search,
      CatalogReadService reads,
      ExternalUrlValidator urls,
      CatalogMutationLock mutation,
      CatalogReadRepository listings) {
    this.current = current;
    this.products = products;
    this.jpa = jpa;
    this.stores = stores;
    this.categories = categories;
    this.media = media;
    this.search = search;
    this.reads = reads;
    this.urls = urls;
    this.mutation = mutation;
    this.listings = listings;
  }

  public ManagedProduct create(ProductWrite r) {
    current.require(Role.SELLER);
    mutation.acquire();
    validate(r);
    var store = stores.requireForSeller();
    var p =
        new Product(
            store,
            categories.requireActive(r.categoryId()),
            Rules.name(r.title(), "title", 160),
            r.description(),
            Rules.money(r.price(), "price"),
            products.lower(Rules.name(r.colour(), "colour", 40)));
    fields(p, r);
    jpa.persist(p);
    jpa.flush();
    children(p, r);
    search.product(p.getId());
    return reads.managed(p.getId());
  }

  public ManagedProduct replace(UUID id, ProductWrite r, Long version) {
    var actor = current.require(Role.SELLER);
    mutation.acquire();
    validate(r);
    var p = products.owned(id, actor.getId(), true);
    Rules.version(version, p.getVersion());
    if (p.getListingStatus() == ListingStatus.ARCHIVED) throw BusinessException.transition();
    fields(p, r);
    children(p, r);
    if (p.getListingStatus() == ListingStatus.ACTIVE) requirePublishable(p);
    products.changed(p, version);
    search.product(id);
    return reads.managed(id);
  }

  public ManagedProduct status(UUID id, ListingStatus status, Long version) {
    Rules.required(status, "listingStatus");
    var actor = current.require(Role.SELLER);
    mutation.acquire();
    var p = products.owned(id, actor.getId(), true);
    Rules.version(version, p.getVersion());
    if (p.getListingStatus() == status) return reads.managed(id);
    if (p.getListingStatus() == ListingStatus.ARCHIVED && status != ListingStatus.DRAFT)
      throw BusinessException.transition();
    if (status == ListingStatus.ACTIVE) requirePublishable(p);
    p.setListingStatus(status);
    products.changed(p, version);
    return reads.managed(id);
  }

  @Transactional(readOnly = true)
  public ManagedProduct own(UUID id) {
    products.owned(id, current.require(Role.SELLER).getId(), false);
    return reads.managed(id);
  }

  @Transactional(readOnly = true)
  public Page<ManagedProduct> ownList(int page, int size) {
    int offset = Rules.offset(page, size);
    var actor = current.require(Role.SELLER);
    var args = Map.of("user", actor.getId());
    var ids = listings.ids("s.user_id=:user", args, "p.updated_at desc,p.id asc", offset, size);
    var rows = listings.products(ids, false);
    return Rules.page(
        ids.stream().map(rows::get).toList(), page, size, listings.count("s.user_id=:user", args));
  }

  private void validate(ProductWrite r) {
    Rules.required(r, "product");
    Rules.required(r.categoryId(), "categoryId");
    Rules.name(r.title(), "title", 160);
    Rules.text(r.description(), "description", 5000, false, false);
    Rules.money(r.price(), "price");
    if (!"INR".equals(r.currency()))
      throw BusinessException.invalid("currency", "Only INR is supported.");
    Rules.name(r.colour(), "colour", 40);
    Rules.text(r.material(), "material", 100, true, false);
    Rules.text(r.occasion(), "occasion", 100, true, false);
    Rules.text(r.brand(), "brand", 100, true, false);
    if (Rules.required(r.stockQuantity(), "stockQuantity") < 0)
      throw BusinessException.invalid("stockQuantity", "Must be nonnegative.");
    Rules.required(r.availabilityEnabled(), "availabilityEnabled");
    Rules.collection(r.sizes(), "sizes", 20);
    Rules.collection(r.sellerTags(), "sellerTags", 20);
    Rules.collection(r.images(), "images", 10);
    urls.validate(r.sourceUrl(), "sourceUrl");
    Rules.text(r.reelCaption(), "reelCaption", 2000, true, false);
  }

  private void fields(Product p, ProductWrite r) {
    p.setCategory(categories.requireActive(r.categoryId()));
    p.setTitle(Rules.name(r.title(), "title", 160));
    p.setDescription(r.description());
    p.setPrice(Rules.money(r.price(), "price"));
    p.setCurrency("INR");
    p.setColour(products.lower(Rules.name(r.colour(), "colour", 40)));
    p.setMaterial(r.material());
    p.setOccasion(r.occasion());
    p.setBrand(r.brand());
    p.setStockQuantity(r.stockQuantity());
    p.setAvailable(r.availabilityEnabled());
    p.setSourceUrl(urls.validate(r.sourceUrl(), "sourceUrl"));
    p.setReelCaption(r.reelCaption() == null || r.reelCaption().isBlank() ? null : r.reelCaption().trim());
  }

  private void children(Product p, ProductWrite r) {
    var sizes = new LinkedHashSet<String>();
    for (String raw : List.copyOf(r.sizes())) {
      String normalized = products.upper(Rules.name(raw, "sizes", 20));
      Rules.name(normalized, "sizes", 20);
      if (!sizes.add(normalized))
        throw BusinessException.invalid("sizes", "Duplicate normalized size.");
    }
    var sellerTags = new LinkedHashMap<String, String>();
    for (String raw : List.copyOf(r.sellerTags())) {
      String tag = Rules.name(raw, "sellerTags", 60), key = products.lower(tag);
      if (sellerTags.putIfAbsent(key, tag) != null)
        throw BusinessException.invalid("sellerTags", "Duplicate normalized tag.");
    }
    var existingTags = products.tags(p.getId());
    for (var t : existingTags)
      if (t.getSource() == TagSource.SYSTEM && sellerTags.containsKey(t.getNormalizedTag()))
        throw BusinessException.invalid(
            "sellerTags",
            "This tag already has SYSTEM provenance; use the tag decision operation.");
    var images = List.copyOf(r.images());
    var assetIds = new TreeSet<UUID>();
    boolean[] positions = new boolean[images.size()];
    for (var i : images) {
      Rules.required(i.assetId(), "images.assetId");
      int order = Rules.required(i.displayOrder(), "images.displayOrder");
      Rules.text(i.altText(), "images.altText", 160, false, false);
      if (order < 0 || order >= images.size() || positions[order] || !assetIds.add(i.assetId()))
        throw BusinessException.invalid(
            "images", "Require distinct assets and contiguous unique orders starting at zero.");
      positions[order] = true;
    }
    var assets = new HashMap<UUID, MediaAsset>();
    for (UUID id : assetIds) {
      assets.put(id, media.ownedReady(id));
      media.requireFreeImage(id, p.getId());
    }
    products.sizes(p.getId()).forEach(jpa::remove);
    existingTags.stream().filter(t -> t.getSource() == TagSource.SELLER).forEach(jpa::remove);
    products.images(p.getId()).forEach(jpa::remove);
    jpa.flush();
    for (String size : sizes) jpa.persist(new ProductSize(p, size));
    sellerTags
        .values()
        .forEach(tag -> jpa.persist(new ProductTag(p, tag, TagSource.SELLER, TagStatus.APPROVED)));
    for (var image : images)
      jpa.persist(
          new ProductImage(
              p, assets.get(image.assetId()), image.displayOrder().shortValue(), image.altText()));
    jpa.flush();
  }

  private void requirePublishable(Product p) {
    stores.requireComplete(p.getSeller());
    categories.requireActive(p.getCategory().getId());
    Rules.name(p.getTitle(), "title", 160);
    Rules.text(p.getDescription(), "description", 5000, false, true);
  }
}
