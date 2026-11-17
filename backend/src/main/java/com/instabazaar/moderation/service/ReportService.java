package com.instabazaar.moderation.service;

import com.instabazaar.catalog.model.Product;
import com.instabazaar.moderation.model.*;
import com.instabazaar.moderation.repository.ReportRepository;
import com.instabazaar.review.model.Review;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportService {
  public enum TargetType {
    PRODUCT,
    REVIEW
  }

  private final CurrentUser current;
  private final ReportRepository reports;
  private final EntityAccess jpa;

  public ReportService(CurrentUser current, ReportRepository reports, EntityAccess jpa) {
    this.current = current;
    this.reports = reports;
    this.jpa = jpa;
  }

  public Contracts.Report create(
      TargetType type, UUID target, ReportReason reason, String details) {
    Rules.required(type, "targetType");
    Rules.required(target, "targetId");
    Rules.required(reason, "reason");
    Rules.text(details, "details", 1000, true, false);
    var actor = current.require();
    Product product = null;
    Review review = null;
    if (type == TargetType.PRODUCT) product = jpa.find(Product.class, target);
    else review = jpa.find(Review.class, target);
    if (reports.openExists(actor.getId(), target, type == TargetType.PRODUCT))
      throw new BusinessException("REPORT_EXISTS", 409, "An open report already exists.");
    var report = new Report(actor, product, review, reason);
    report.setDetails(details);
    jpa.persist(report);
    jpa.flush();
    return map(report);
  }

  public static Contracts.Report map(Report r) {
    return new Contracts.Report(
        r.getId(),
        r.getProduct() == null ? "REVIEW" : "PRODUCT",
        r.getProduct() == null ? r.getReview().getId() : r.getProduct().getId(),
        r.getReason().name(),
        r.getDetails(),
        r.getStatus().name(),
        r.getResolutionNote(),
        r.getResolvedAt(),
        r.getVersion(),
        r.getCreatedAt());
  }
}
