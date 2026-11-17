package com.instabazaar.review.controller;

import com.instabazaar.review.service.ReviewService;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.Requests;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {
  private final ReviewService reviews;

  public ReviewController(ReviewService reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/products/{id}/reviews")
  public Contracts.Page<Contracts.Review> publicReviews(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return reviews.publicReviews(id, page, size);
  }

  @PostMapping("/products/{id}/reviews")
  public ResponseEntity<Contracts.Review> create(
      @PathVariable UUID id, @Valid @RequestBody Requests.CreateReview body) {
    var review = reviews.create(id, body.rating(), body.text());
    return ResponseEntity.created(URI.create("/api/v1/reviews/" + review.id())).body(review);
  }

  @PutMapping("/reviews/{id}")
  public Contracts.Review replace(
      @PathVariable UUID id, @Valid @RequestBody Requests.UpdateReview body) {
    return reviews.replace(id, body.rating(), body.text(), body.version());
  }

  @GetMapping("/me/reviews")
  public Contracts.Page<Contracts.Review> own(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return reviews.own(page, size);
  }
}
