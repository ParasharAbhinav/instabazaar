package com.instabazaar.review.service;

import com.instabazaar.catalog.model.Product;
import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.review.model.*;
import com.instabazaar.review.repository.ReviewRepository;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
public class ReviewService {
  private final CurrentUser current;
  private final ReviewRepository reviews;
  private final EntityAccess jpa;
  private final CatalogReadService catalog;

  public ReviewService(
      CurrentUser current, ReviewRepository reviews, EntityAccess jpa, CatalogReadService catalog) {
    this.current = current;
    this.reviews = reviews;
    this.jpa = jpa;
    this.catalog = catalog;
  }

  public Contracts.Review create(UUID productId, Integer rating, String text) {
    var actor = current.require();
    validate(rating, text);
    catalog.requirePublic(productId);
    var product = jpa.find(Product.class, productId);
    if (product.getSeller().getUser().getId().equals(actor.getId()))
      throw new BusinessException("SELF_REVIEW", 422, "You cannot review your own listing.");
    if (reviews.exists(actor.getId(), productId))
      throw new BusinessException("REVIEW_EXISTS", 409, "A review already exists.");
    var review = new Review(actor, product, rating.shortValue());
    review.setReviewText(text);
    jpa.persist(review);
    jpa.flush();
    return map(review);
  }

  public Contracts.Review replace(UUID reviewId, Integer rating, String text, Long version) {
    var actor = current.require();
    validate(rating, text);
    var review = reviews.lock(Rules.required(reviewId, "reviewId"));
    if (!review.getReviewer().getId().equals(actor.getId())) throw BusinessException.missing();
    Rules.version(version, review.getVersion());
    catalog.requirePublic(review.getProduct().getId());
    review.setRating(rating.shortValue());
    review.setReviewText(text);
    review.setStatus(ReviewStatus.PENDING);
    jpa.flush();
    if (review.getVersion().equals(version)) jpa.increment(review);
    return map(review);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Contracts.Page<Contracts.Review> own(int page, int size) {
    int offset = Rules.offset(page, size);
    var args = Map.of("user", current.require().getId());
    String where = "r.reviewer_id=:user";
    return Rules.page(
        reviews.list(where, args, offset, size), page, size, reviews.count(where, args));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Contracts.Page<Contracts.Review> publicReviews(UUID productId, int page, int size) {
    int offset = Rules.offset(page, size);
    catalog.requirePublic(productId);
    var args = Map.of("product", productId);
    String where = "r.product_id=:product and r.status='APPROVED'";
    return Rules.page(
        reviews.list(where, args, offset, size), page, size, reviews.count(where, args));
  }

  private void validate(Integer rating, String text) {
    if (Rules.required(rating, "rating") < 1 || rating > 5)
      throw BusinessException.invalid("rating", "Use an integer from 1 to 5.");
    Rules.text(text, "text", 2000, true, false);
  }

  public static Contracts.Review map(Review r) {
    return new Contracts.Review(
        r.getId(),
        r.getProduct().getId(),
        new Contracts.Reviewer(r.getReviewer().getId(), r.getReviewer().getName()),
        r.getRating(),
        r.getReviewText(),
        r.getStatus().name(),
        r.getVersion(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
