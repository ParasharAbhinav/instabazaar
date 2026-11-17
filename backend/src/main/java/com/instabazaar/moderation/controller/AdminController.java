package com.instabazaar.moderation.controller;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.model.ListingStatus;
import com.instabazaar.catalog.model.ModerationStatus;
import com.instabazaar.moderation.model.ReportStatus;
import com.instabazaar.moderation.service.AdminQueryService;
import com.instabazaar.moderation.service.ModerationService;
import com.instabazaar.moderation.service.ReportService.TargetType;
import com.instabazaar.review.model.ReviewStatus;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminQueryService queries;
  private final ModerationService moderation;

  public AdminController(AdminQueryService queries, ModerationService moderation) {
    this.queries = queries;
    this.moderation = moderation;
  }

  @GetMapping("/statistics")
  public Contracts.Statistics statistics() {
    return queries.statistics();
  }

  @GetMapping("/users")
  public Contracts.Page<Contracts.User> users(
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.users(role, enabled, page, size);
  }

  @PatchMapping("/users/{id}/status")
  public Contracts.User account(
      @PathVariable UUID id, @Valid @RequestBody Requests.AccountStatus body) {
    return moderation.account(id, body.enabled(), body.reason(), body.version());
  }

  @GetMapping("/products")
  public Contracts.Page<Contracts.ManagedProduct> products(
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) ListingStatus listingStatus,
      @RequestParam(required = false) ModerationStatus moderationStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.products(sellerId, listingStatus, moderationStatus, page, size);
  }

  @PatchMapping("/products/{id}/moderation")
  public Contracts.ManagedProduct product(
      @PathVariable UUID id, @Valid @RequestBody Requests.ProductModeration body) {
    return moderation.product(id, body.moderationStatus(), body.reason(), body.version());
  }

  @GetMapping("/reviews")
  public Contracts.Page<Contracts.Review> reviews(
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) ReviewStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.reviews(productId, status, page, size);
  }

  @PatchMapping("/reviews/{id}/moderation")
  public Contracts.Review review(
      @PathVariable UUID id, @Valid @RequestBody Requests.ReviewModeration body) {
    return moderation.review(id, body.status(), body.reason(), body.version());
  }

  @GetMapping("/reports")
  public Contracts.Page<Contracts.Report> reports(
      @RequestParam(required = false) TargetType targetType,
      @RequestParam(required = false) ReportStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return queries.reports(targetType, status, page, size);
  }

  @PatchMapping("/reports/{id}")
  public Contracts.Report resolve(
      @PathVariable UUID id, @Valid @RequestBody Requests.ReportResolution body) {
    return moderation.closeReport(id, body.status(), body.resolutionNote(), body.version());
  }
}
