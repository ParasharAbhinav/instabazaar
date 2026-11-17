package com.instabazaar.wishlist.controller;

import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.wishlist.service.WishlistService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/wishlist")
public class WishlistController {
  private final WishlistService wishlist;

  public WishlistController(WishlistService wishlist) {
    this.wishlist = wishlist;
  }

  @GetMapping
  public Contracts.Page<Contracts.SavedProduct> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return wishlist.list(page, size);
  }

  @PutMapping("/{productId}")
  public ResponseEntity<Void> save(@PathVariable UUID productId) {
    wishlist.save(productId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{productId}")
  public ResponseEntity<Void> remove(@PathVariable UUID productId) {
    wishlist.remove(productId);
    return ResponseEntity.noContent().build();
  }
}
