package com.instabazaar.entity;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, updatable = false)
    public Instant createdAt = Instant.now();
    @Version
    public long version;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
