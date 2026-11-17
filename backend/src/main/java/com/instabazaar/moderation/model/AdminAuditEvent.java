package com.instabazaar.moderation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.CreatedEntity;
import com.instabazaar.account.model.User;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Immutable;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "admin_audit_event")
@Immutable
public class AdminAuditEvent extends CreatedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private AuditTargetType targetType;

    @NotNull
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @NotBlank
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", nullable = false, columnDefinition = "jsonb")
    private String beforeState;

    @NotBlank
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", nullable = false, columnDefinition = "jsonb")
    private String afterState;

    protected AdminAuditEvent() {}

    public AdminAuditEvent(User actor, AuditAction action, AuditTargetType targetType, UUID targetId, String reason, String beforeState, String afterState) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.action = Objects.requireNonNull(action, "action");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.beforeState = Objects.requireNonNull(beforeState, "beforeState");
        this.afterState = Objects.requireNonNull(afterState, "afterState");
    }

    public User getActor() { return actor; }

    public AuditAction getAction() { return action; }

    public AuditTargetType getTargetType() { return targetType; }

    public UUID getTargetId() { return targetId; }

    public String getReason() { return reason; }

    public String getBeforeState() { return beforeState; }

    public String getAfterState() { return afterState; }

}
