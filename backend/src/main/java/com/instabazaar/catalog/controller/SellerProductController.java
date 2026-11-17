package com.instabazaar.catalog.controller;

import com.instabazaar.catalog.service.ProductService;
import com.instabazaar.catalog.service.TagService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.validation.Rules;
import com.instabazaar.shared.web.Requests;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/products")
public class SellerProductController {
  private final ProductService products;
  private final TagService tags;

  public SellerProductController(ProductService products, TagService tags) {
    this.products = products;
    this.tags = tags;
  }

  @GetMapping
  public Contracts.Page<Contracts.ManagedProduct> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return products.ownList(page, size);
  }

  @GetMapping("/{id}")
  public Contracts.ManagedProduct detail(@PathVariable UUID id) {
    return products.own(id);
  }

  @PostMapping
  public ResponseEntity<Contracts.ManagedProduct> create(
      @Valid @RequestBody Requests.Product body) {
    if (body.version() != null) {
      throw BusinessException.invalid("version", "Omit version when creating a product.");
    }
    var product = products.create(body.toWrite());
    return ResponseEntity.created(URI.create("/api/v1/seller/products/" + product.product().id()))
        .body(product);
  }

  @PutMapping("/{id}")
  public Contracts.ManagedProduct replace(
      @PathVariable UUID id, @Valid @RequestBody Requests.Product body) {
    Rules.required(body.version(), "version");
    return products.replace(id, body.toWrite(), body.version());
  }

  @PatchMapping("/{id}/status")
  public Contracts.ManagedProduct status(
      @PathVariable UUID id, @Valid @RequestBody Requests.ProductStatus body) {
    return products.status(id, body.listingStatus(), body.version());
  }

  @GetMapping("/{id}/tags")
  public Contracts.TagSet tags(@PathVariable UUID id) {
    return tags.list(id);
  }

  @PostMapping("/{id}/tag-suggestions")
  public Contracts.TagSet suggest(
      @PathVariable UUID id, @Valid @RequestBody Requests.Version body) {
    return tags.suggest(id, body.version());
  }

  @PatchMapping("/{id}/tags/{tagId}")
  public Contracts.TagSet decide(
      @PathVariable UUID id,
      @PathVariable UUID tagId,
      @Valid @RequestBody Requests.TagDecision body) {
    return tags.decide(id, tagId, body.status(), body.productVersion());
  }
}
