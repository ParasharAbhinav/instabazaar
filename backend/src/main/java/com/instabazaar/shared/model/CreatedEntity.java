package com.instabazaar.shared.model;

import jakarta.persistence.*;
import java.time.Instant;
@MappedSuperclass
public abstract class CreatedEntity extends BaseEntity {
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;
    protected CreatedEntity() {}
    @PrePersist
    protected void initializeCreatedAt() { createdAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
}
