package com.instabazaar.moderation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.CreatedEntity;
import com.instabazaar.account.model.User;
import com.instabazaar.catalog.model.Product;
import com.instabazaar.review.model.Review;
import java.time.Instant;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "report")
public class Report extends CreatedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "review_id", nullable = true, updatable = false)
    private Review review;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReportReason reason;

    @Size(max = 1000)
    @Column(name = "details", nullable = true, length = 1000)
    private String details;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status = ReportStatus.OPEN;

    @Size(max = 1000)
    @Column(name = "resolution_note", nullable = true, length = 1000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by", nullable = true)
    private User resolvedBy;

    @Column(name = "resolved_at", nullable = true, columnDefinition = "timestamptz")
    private Instant resolvedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Report() {}

    public Report(User reporter, Product product, Review review, ReportReason reason) {
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.product = product;
        this.review = review;
        this.reason = Objects.requireNonNull(reason, "reason");
        if ((product == null) == (review == null)) throw new IllegalArgumentException("Exactly one report target is required");
    }

    public User getReporter() { return reporter; }

    public Product getProduct() { return product; }

    public Review getReview() { return review; }

    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String getDetails() { return details; }
    public void setDetails(String details) {
        this.details = details;
    }

    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    public User getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(User resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getVersion() { return version; }
}
