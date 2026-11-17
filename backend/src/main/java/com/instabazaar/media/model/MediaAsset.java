package com.instabazaar.media.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.CreatedEntity;
import com.instabazaar.account.model.User;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "media_asset")
public class MediaAsset extends CreatedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @NotBlank
    @Size(max = 512)
    @Column(name = "storage_key", nullable = false, length = 512, unique = true, updatable = false)
    private String storageKey;

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "image/(jpeg|png|webp)")
    @Column(name = "content_type", nullable = false, length = 30, updatable = false)
    private String contentType;

    @Min(0)
    @Positive
    @Max(5242880)
    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Min(0)
    @Positive
    @Column(name = "width", nullable = false, updatable = false)
    private int width;

    @Min(0)
    @Positive
    @Column(name = "height", nullable = false, updatable = false)
    private int height;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 10)
    private MediaState state = MediaState.READY;

    protected MediaAsset() {}

    public MediaAsset(User owner, String storageKey, String contentType, long byteSize, int width, int height) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
    }

    public User getOwner() { return owner; }

    public String getStorageKey() { return storageKey; }

    public String getContentType() { return contentType; }

    public long getByteSize() { return byteSize; }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public MediaState getState() { return state; }
    public void setState(MediaState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

}
