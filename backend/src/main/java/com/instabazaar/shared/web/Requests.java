package com.instabazaar.shared.web;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.dto.ProductWrite;
import com.instabazaar.catalog.model.ListingStatus;
import com.instabazaar.catalog.model.ModerationStatus;
import com.instabazaar.catalog.model.TagStatus;
import com.instabazaar.moderation.model.ReportReason;
import com.instabazaar.moderation.model.ReportStatus;
import com.instabazaar.moderation.service.ReportService.TargetType;
import com.instabazaar.review.model.ReviewStatus;
import com.instabazaar.storefront.dto.StoreWrite;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** HTTP input only. Actor identity and protected fields never come from a request. */
public final class Requests {
  private Requests() {}

  public record Register(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 12, max = 128) String password,
      @NotNull Role role) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 1, max = 128) String password) {}

  public record Profile(
      @NotBlank @Size(max = 100) String name, @NotNull @PositiveOrZero Long version) {}

  public record Product(
      @NotNull UUID categoryId,
      @NotBlank @Size(max = 160) String title,
      @NotNull @Size(max = 5000) String description,
      @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal price,
      @NotBlank String currency,
      @NotBlank @Size(max = 40) String colour,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 20) String> sizes,
      @Size(max = 100) String material,
      @Size(max = 100) String occasion,
      @Size(max = 100) String brand,
      @NotNull @PositiveOrZero Integer stockQuantity,
      @NotNull Boolean availabilityEnabled,
      @Size(max = 2048) String sourceUrl,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 60) String> sellerTags,
      @NotNull @Size(max = 10) List<@NotNull @Valid Image> images,
      @Size(max = 2000) String reelCaption,
      @PositiveOrZero Long version) {
    public ProductWrite toWrite() {
      return new ProductWrite(
          categoryId,
          title,
          description,
          price,
          currency,
          colour,
          sizes,
          material,
          occasion,
          brand,
          stockQuantity,
          availabilityEnabled,
          sourceUrl,
          sellerTags,
          images.stream()
              .map(
                  image ->
                      new ProductWrite.ImageWrite(
                          image.assetId(), image.displayOrder(), image.altText()))
              .toList(),
          reelCaption);
    }
  }

  public record Image(
      @NotNull UUID assetId,
      @NotNull @PositiveOrZero Integer displayOrder,
      @NotNull @Size(max = 160) String altText) {}

  public record ProductStatus(
      @NotNull ListingStatus listingStatus, @NotNull @PositiveOrZero Long version) {}

  public record Version(@NotNull @PositiveOrZero Long version) {}

  public record TagDecision(
      @NotNull TagStatus status, @NotNull @PositiveOrZero Long productVersion) {}

  public record Store(
      @NotBlank @Size(max = 120) String storeName,
      @NotNull @Size(max = 2000) String description,
      UUID logoAssetId,
      @Email @Size(max = 254) String publicEmail,
      @Size(max = 20) String publicPhone,
      @Size(max = 20) String whatsappNumber,
      @Size(max = 2048) String instagramUrl,
      @Size(max = 2048) String facebookUrl,
      @PositiveOrZero Long version) {
    public StoreWrite toWrite() {
      return new StoreWrite(
          storeName,
          description,
          logoAssetId,
          publicEmail,
          publicPhone,
          whatsappNumber,
          instagramUrl,
          facebookUrl,
          version);
    }
  }

  public record CreateReview(
      @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String text) {}

  public record UpdateReview(
      @NotNull @Min(1) @Max(5) Integer rating,
      @Size(max = 2000) String text,
      @NotNull @PositiveOrZero Long version) {}

  public record Report(
      @NotNull TargetType targetType,
      @NotNull UUID targetId,
      @NotNull ReportReason reason,
      @Size(max = 1000) String details) {}

  public record AccountStatus(
      @NotNull Boolean enabled,
      @NotBlank @Size(max = 1000) String reason,
      @NotNull @PositiveOrZero Long version) {}

  public record ProductModeration(
      @NotNull ModerationStatus moderationStatus,
      @NotBlank @Size(max = 1000) String reason,
      @NotNull @PositiveOrZero Long version) {}

  public record ReviewModeration(
      @NotNull ReviewStatus status,
      @NotBlank @Size(max = 1000) String reason,
      @NotNull @PositiveOrZero Long version) {}

  public record ReportResolution(
      @NotNull ReportStatus status,
      @NotBlank @Size(max = 1000) String resolutionNote,
      @NotNull @PositiveOrZero Long version) {}
}
