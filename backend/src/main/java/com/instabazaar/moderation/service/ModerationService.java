package com.instabazaar.moderation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.account.model.*;
import com.instabazaar.account.repository.UserRepository;
import com.instabazaar.account.service.AccountService;
import com.instabazaar.catalog.model.*;
import com.instabazaar.catalog.repository.ProductRepository;
import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.moderation.model.*;
import com.instabazaar.moderation.repository.ReportRepository;
import com.instabazaar.review.model.*;
import com.instabazaar.review.repository.ReviewRepository;
import com.instabazaar.review.service.ReviewService;
import com.instabazaar.security.CurrentUser;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.repository.EntityAccess;
import com.instabazaar.shared.validation.Rules;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModerationService {
  private final CurrentUser current;
  private final UserRepository users;
  private final ProductRepository products;
  private final ReviewRepository reviews;
  private final ReportRepository reports;
  private final EntityAccess jpa;
  private final CatalogReadService reads;
  private final ObjectMapper json;
  private final Clock clock;

  public ModerationService(
      CurrentUser current,
      UserRepository users,
      ProductRepository products,
      ReviewRepository reviews,
      ReportRepository reports,
      EntityAccess jpa,
      CatalogReadService reads,
      ObjectMapper json,
      Clock clock) {
    this.current = current;
    this.users = users;
    this.products = products;
    this.reviews = reviews;
    this.reports = reports;
    this.jpa = jpa;
    this.reads = reads;
    this.json = json;
    this.clock = clock;
  }

  public Contracts.User account(UUID id, Boolean enabled, String reason, Long version) {
    Rules.required(enabled, "enabled");
    reason = Rules.name(reason, "reason", 1000);
    users.serializeAccountChanges();
    var actor = current.require(Role.ADMIN);
    var target = users.lock(Rules.required(id, "userId"));
    Rules.version(version, target.getVersion());
    if (!enabled && actor.getId().equals(id)) throw BusinessException.transition();
    if (target.isEnabled() == enabled) return AccountService.map(target);
    if (!enabled && target.getRole() == Role.ADMIN && users.enabledAdmins() <= 1)
      throw BusinessException.transition();
    var before =
        Map.<String, Object>of("enabled", target.isEnabled(), "version", target.getVersion());
    target.setEnabled(enabled);
    jpa.flush();
    audit(
        actor,
        AuditAction.ACCOUNT_STATUS,
        AuditTargetType.USER,
        id,
        reason,
        before,
        Map.of("enabled", target.isEnabled(), "version", target.getVersion()));
    return AccountService.map(target);
  }

  public Contracts.ManagedProduct product(
      UUID id, ModerationStatus status, String reason, Long version) {
    Rules.required(status, "moderationStatus");
    reason = Rules.name(reason, "reason", 1000);
    var actor = current.require(Role.ADMIN);
    var p = products.lock(Rules.required(id, "productId"));
    Rules.version(version, p.getVersion());
    if (p.getModerationStatus() == status) return reads.managed(id);
    var before =
        Map.<String, Object>of(
            "moderationStatus", p.getModerationStatus().name(), "version", p.getVersion());
    p.setModerationStatus(status);
    jpa.flush();
    audit(
        actor,
        AuditAction.PRODUCT_MODERATION,
        AuditTargetType.PRODUCT,
        id,
        reason,
        before,
        Map.of("moderationStatus", status.name(), "version", p.getVersion()));
    return reads.managed(id);
  }

  public Contracts.Review review(UUID id, ReviewStatus status, String reason, Long version) {
    if (status != ReviewStatus.APPROVED && status != ReviewStatus.REJECTED)
      throw BusinessException.invalid("status", "Choose APPROVED or REJECTED.");
    reason = Rules.name(reason, "reason", 1000);
    var actor = current.require(Role.ADMIN);
    var r = reviews.lock(Rules.required(id, "reviewId"));
    Rules.version(version, r.getVersion());
    if (r.getStatus() == status) return ReviewService.map(r);
    var before = Map.<String, Object>of("status", r.getStatus().name(), "version", r.getVersion());
    r.setStatus(status);
    jpa.flush();
    audit(
        actor,
        AuditAction.REVIEW_MODERATION,
        AuditTargetType.REVIEW,
        id,
        reason,
        before,
        Map.of("status", status.name(), "version", r.getVersion()));
    return ReviewService.map(r);
  }

  public Contracts.Report closeReport(UUID id, ReportStatus status, String note, Long version) {
    if (status != ReportStatus.RESOLVED && status != ReportStatus.DISMISSED)
      throw BusinessException.invalid("status", "Choose RESOLVED or DISMISSED.");
    note = Rules.name(note, "resolutionNote", 1000);
    var actor = current.require(Role.ADMIN);
    var r = reports.lock(Rules.required(id, "reportId"));
    Rules.version(version, r.getVersion());
    if (r.getStatus() != ReportStatus.OPEN) throw BusinessException.transition();
    var before = Map.<String, Object>of("status", r.getStatus().name(), "version", r.getVersion());
    r.setStatus(status);
    r.setResolutionNote(note);
    r.setResolvedBy(actor);
    r.setResolvedAt(clock.instant());
    jpa.flush();
    audit(
        actor,
        AuditAction.REPORT_RESOLUTION,
        AuditTargetType.REPORT,
        id,
        note,
        before,
        Map.of(
            "status",
            status.name(),
            "version",
            r.getVersion(),
            "resolvedBy",
            actor.getId().toString(),
            "resolvedAt",
            r.getResolvedAt().toString()));
    return ReportService.map(r);
  }

  private void audit(
      User actor,
      AuditAction action,
      AuditTargetType type,
      UUID id,
      String reason,
      Map<String, Object> before,
      Map<String, Object> after) {
    jpa.persist(
        new AdminAuditEvent(actor, action, type, id, reason, auditJson(before), auditJson(after)));
    jpa.flush();
  }

  private String auditJson(Map<String, Object> value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Unable to serialize the audit event.", failure);
    }
  }
}
