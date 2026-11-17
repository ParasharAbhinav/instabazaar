package com.instabazaar.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductWrite(
    UUID categoryId,
    String title,
    String description,
    BigDecimal price,
    String currency,
    String colour,
    List<String> sizes,
    String material,
    String occasion,
    String brand,
    Integer stockQuantity,
    Boolean availabilityEnabled,
    String sourceUrl,
    List<String> sellerTags,
    List<ImageWrite> images,
    String reelCaption) {
  // Existing service callers can omit the optional seller-only caption.
  public ProductWrite(UUID categoryId, String title, String description, BigDecimal price,
      String currency, String colour, List<String> sizes, String material, String occasion,
      String brand, Integer stockQuantity, Boolean availabilityEnabled, String sourceUrl,
      List<String> sellerTags, List<ImageWrite> images) {
    this(categoryId, title, description, price, currency, colour, sizes, material, occasion,
        brand, stockQuantity, availabilityEnabled, sourceUrl, sellerTags, images, null);
  }
  public record ImageWrite(UUID assetId, Integer displayOrder, String altText) {}
}
