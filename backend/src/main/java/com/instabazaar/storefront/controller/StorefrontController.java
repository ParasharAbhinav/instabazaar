package com.instabazaar.storefront.controller;

import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.storefront.service.StorefrontService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sellers")
public class StorefrontController {
  private final StorefrontService stores;

  public StorefrontController(StorefrontService stores) {
    this.stores = stores;
  }

  @GetMapping("/{id}")
  public Contracts.Store detail(@PathVariable UUID id) {
    return stores.publicStore(id);
  }
}
