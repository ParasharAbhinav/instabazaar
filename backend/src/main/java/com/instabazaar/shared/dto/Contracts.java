package com.instabazaar.shared.dto;

import com.instabazaar.shared.exception.BusinessException.FieldError;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exact architecture response shapes. These records never contain persistence entities. */
public final class Contracts {
  private Contracts() {}

  public record Page<T>(List<T> items, int page, int size, long totalElements, long totalPages) {
    public Page {
      items = List.copyOf(items);
    }
  }

  public record Items<T>(List<T> items) {
    public Items {
      items = List.copyOf(items);
    }
  }

  public record Error(
      String code,
      String message,
      List<FieldError> fieldErrors,
      String traceId,
      Instant timestamp) {}

  /** HTTP error contract; retains the existing Tier 2 Error wire shape. */
  public record ApiError(
      String code,
      String message,
      List<FieldError> fieldErrors,
      String traceId,
      Instant timestamp) {
    public ApiError {
      fieldErrors = List.copyOf(fieldErrors);
    }
  }

  public record User(
      UUID id,
      String name,
      String email,
      String role,
      boolean enabled,
      long version,
      Instant createdAt) {}

  public record Category(UUID id, String name, String slug, String description) {}

  public record Rating(BigDecimal average, long count) {}

  public record SellerSummary(UUID id, String storeName) {}

  public record Store(
      UUID id,
      String storeName,
      String description,
      String logoUrl,
      String publicEmail,
      String publicPhone,
      String whatsappNumber,
      String instagramUrl,
      String facebookUrl,
      Rating rating,
      Instant createdAt,
      long version) {}

  public record Image(UUID id, UUID assetId, String url, int displayOrder, String altText) {}

  public record Asset(
      UUID id, String previewUrl, String contentType, long byteSize, int width, int height) {}

  public record ProductSummary(
      UUID id,
      String title,
      String price,
      String currency,
      boolean available,
      String thumbnailUrl,
      SellerSummary seller,
      Category category,
      Rating rating) {}

  public record ProductDetail(
      UUID id,
      String title,
      String description,
      String price,
      String currency,
      String colour,
      List<String> sizes,
      String material,
      String occasion,
      String brand,
      int stockQuantity,
      boolean available,
      String sourceUrl,
      Store seller,
      Category category,
      List<Image> images,
      List<String> tags,
      Rating rating,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    public ProductDetail {
      sizes = List.copyOf(sizes);
      images = List.copyOf(images);
      tags = List.copyOf(tags);
    }
  }

  public record ManagedProduct(
      ProductDetail product,
      boolean availabilityEnabled,
      String listingStatus,
      String moderationStatus,
      String reelCaption) {
    public ManagedProduct(ProductDetail product, boolean availabilityEnabled,
        String listingStatus, String moderationStatus) {
      this(product, availabilityEnabled, listingStatus, moderationStatus, null);
    }
  }

  public record Tag(UUID id, String tag, String source, String status) {}

  public record TagSet(List<Tag> items, long productVersion) {
    public TagSet {
      items = List.copyOf(items);
    }
  }

  public record Reviewer(UUID id, String name) {}

  public record Review(
      UUID id,
      UUID productId,
      Reviewer reviewer,
      int rating,
      String text,
      String status,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record SavedProduct(
      UUID productId, Instant savedAt, String visibility, ProductSummary product) {}

  public record Report(
      UUID id,
      String targetType,
      UUID targetId,
      String reason,
      String details,
      String status,
      String resolutionNote,
      Instant resolvedAt,
      long version,
      Instant createdAt) {}

  public record Statistics(
      long enabledUsers,
      long sellerStores,
      long publicProducts,
      long pendingReviews,
      long openReports) {}
}
