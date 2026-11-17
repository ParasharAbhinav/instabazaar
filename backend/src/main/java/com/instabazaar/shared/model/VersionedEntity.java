package com.instabazaar.shared.model;

import jakarta.persistence.*;
/** Null version marks a new assigned-ID entity for Spring Data JPA. */
@MappedSuperclass
public abstract class VersionedEntity extends AuditedEntity {
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    protected VersionedEntity() {}
    public Long getVersion() { return version; }
}
