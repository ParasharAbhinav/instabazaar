package com.instabazaar.storefront.controller;

import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import com.instabazaar.storefront.service.StorefrontService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/store")
public class SellerStoreController {
  private final StorefrontService stores;

  public SellerStoreController(StorefrontService stores) {
    this.stores = stores;
  }

  @GetMapping
  public Contracts.Store own() {
    return stores.own();
  }

  @PutMapping
  public ResponseEntity<Contracts.Store> replace(@Valid @RequestBody Requests.Store body) {
    var store = stores.replace(body.toWrite());
    return body.version() == null
        ? ResponseEntity.created(URI.create("/api/v1/sellers/" + store.id())).body(store)
        : ResponseEntity.ok(store);
  }
}
