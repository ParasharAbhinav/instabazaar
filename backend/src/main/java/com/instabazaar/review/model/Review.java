package com.instabazaar.review.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.VersionedEntity;
import com.instabazaar.account.model.User;
import com.instabazaar.catalog.model.Product;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "review")
public class Review extends VersionedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false, updatable = false)
    private User reviewer;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private short rating;

    @Size(max = 2000)
    @Column(name = "review_text", nullable = true, length = 2000)
    private String reviewText;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReviewStatus status = ReviewStatus.PENDING;

    protected Review() {}

    public Review(User reviewer, Product product, short rating) {
        this.reviewer = Objects.requireNonNull(reviewer, "reviewer");
        this.product = Objects.requireNonNull(product, "product");
        this.rating = rating;
    }

    public User getReviewer() { return reviewer; }

    public Product getProduct() { return product; }

    public short getRating() { return rating; }
    public void setRating(short rating) {
        this.rating = rating;
    }

    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

}
