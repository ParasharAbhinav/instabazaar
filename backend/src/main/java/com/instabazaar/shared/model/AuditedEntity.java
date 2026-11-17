package com.instabazaar.shared.model;

import jakarta.persistence.*;
import java.time.Instant;
@MappedSuperclass
public abstract class AuditedEntity extends CreatedEntity {
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;
    protected AuditedEntity() {}
    @PrePersist
    protected void initializeUpdatedAt() { updatedAt = getCreatedAt(); }
    @PreUpdate
    protected void updateTimestamp() { updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
