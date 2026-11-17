package com.instabazaar.shared.model;

import jakarta.persistence.*;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
/** UUID identity is assigned in Java; no identity setters or recursive equality. */
@MappedSuperclass
@Access(AccessType.FIELD)
public abstract class BaseEntity implements Persistable<UUID> {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();
    @Transient
    private boolean newEntity = true;
    protected BaseEntity() {}
    public UUID getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostPersist
    @PostLoad
    protected void markPersisted() { newEntity = false; }
}
