package com.instabazaar.video;

import com.instabazaar.entity.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "video_jobs")
public class VideoJob extends BaseEntity {
    public enum Status { QUEUED, PROCESSING, READY, REVIEWED, APPLIED, FAILED }
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public AppUser owner;
    @ManyToOne(fetch = FetchType.LAZY)
    public Product product;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 16)
    public Status status = Status.QUEUED;
    @Column(nullable = false, length = 36)
    public String storageKey;
    @Column(nullable = false, length = 8000)
    public String suggestions = "[]";
    @Column(nullable = false, length = 1000)
    public String approvedTags = "";
    @Column(nullable = false, length = 300)
    public String message = "Waiting for the local visual analyser.";
    @Column(nullable = false)
    public int frameCount;
    @Column(nullable = false)
    public double duration;
    public AppUser getOwner() { return owner; }
    public Product getProduct() { return product; }
    public Status getStatus() { return status; }
    public String getStorageKey() { return storageKey; }
    public String getSuggestions() { return suggestions; }
    public String getApprovedTags() { return approvedTags; }
    public String getMessage() { return message; }
    public int getFrameCount() { return frameCount; }
    public double getDuration() { return duration; }
}
