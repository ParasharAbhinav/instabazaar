package com.instabazaar.moderation.service;

import com.instabazaar.account.model.Role;
import com.instabazaar.catalog.model.ListingStatus;
import com.instabazaar.catalog.model.ModerationStatus;
import com.instabazaar.catalog.repository.CatalogReadRepository;
import com.instabazaar.moderation.model.ReportStatus;
import com.instabazaar.moderation.repository.AdminQueryRepository;
import com.instabazaar.review.model.ReviewStatus;
import com.instabazaar.review.repository.ReviewRepository;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts.*;
import com.instabazaar.shared.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminQueryService {
  private final CurrentUser current;
  private final AdminQueryRepository repository;
  private final CatalogReadRepository products;
  private final ReviewRepository reviews;

  public AdminQueryService(
      CurrentUser current,
      AdminQueryRepository repository,
      CatalogReadRepository products,
      ReviewRepository reviews) {
    this.current = current;
    this.repository = repository;
    this.products = products;
    this.reviews = reviews;
  }

  public Statistics statistics() {
    current.require(Role.ADMIN);
    return repository.statistics();
  }

  public Page<User> users(Role role, Boolean enabled, int page, int size) {
    current.require(Role.ADMIN);
    int offset = Rules.offset(page, size);
    String where = "true";
    var args = new HashMap<String, Object>();
    if (role != null) {
      where += " and role=:role";
      args.put("role", role.name());
    }
    if (enabled != null) {
      where += " and enabled=:enabled";
      args.put("enabled", enabled);
    }
    return Rules.page(
        repository.users(where, args, offset, size),
        page,
        size,
        repository.count("app_user", where, args));
  }

  public Page<ManagedProduct> products(
      UUID sellerId, ListingStatus listing, ModerationStatus moderation, int page, int size) {
    current.require(Role.ADMIN);
    int offset = Rules.offset(page, size);
    String where = "true";
    var args = new HashMap<String, Object>();
    if (sellerId != null) {
      where += " and p.seller_id=:seller";
      args.put("seller", sellerId);
    }
    if (listing != null) {
      where += " and p.listing_status=:listing";
      args.put("listing", listing.name());
    }
    if (moderation != null) {
      where += " and p.moderation_status=:moderation";
      args.put("moderation", moderation.name());
    }
    var ids = products.ids(where, args, "p.created_at desc,p.id asc", offset, size);
    var rows = products.products(ids, false);
    return Rules.page(
        ids.stream().map(rows::get).toList(), page, size, products.count(where, args));
  }

  public Page<Review> reviews(UUID productId, ReviewStatus status, int page, int size) {
    current.require(Role.ADMIN);
    int offset = Rules.offset(page, size);
    String where = "true";
    var args = new HashMap<String, Object>();
    if (productId != null) {
      where += " and r.product_id=:product";
      args.put("product", productId);
    }
    if (status != null) {
      where += " and r.status=:status";
      args.put("status", status.name());
    }
    return Rules.page(
        reviews.list(where, args, offset, size), page, size, reviews.count(where, args));
  }

  public Page<Report> reports(
      ReportService.TargetType type, ReportStatus status, int page, int size) {
    current.require(Role.ADMIN);
    int offset = Rules.offset(page, size);
    String where = "true";
    var args = new HashMap<String, Object>();
    if (type != null)
      where +=
          type == ReportService.TargetType.PRODUCT
              ? " and product_id is not null"
              : " and review_id is not null";
    if (status != null) {
      where += " and status=:status";
      args.put("status", status.name());
    }
    return Rules.page(
        repository.reports(where, args, offset, size),
        page,
        size,
        repository.count("report", where, args));
  }
}
