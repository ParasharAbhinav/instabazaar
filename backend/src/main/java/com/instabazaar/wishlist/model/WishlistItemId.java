package com.instabazaar.wishlist.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
@Embeddable
public class WishlistItemId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "user_id", nullable = false)
    @NotNull
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    @NotNull
    private UUID productId;

    protected WishlistItemId() {}
    public WishlistItemId(UUID userId, UUID productId) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.productId = Objects.requireNonNull(productId, "productId");
    }
    public UUID getUserId() { return userId; }
    public UUID getProductId() { return productId; }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WishlistItemId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(productId, that.productId);
    }
    @Override
    public int hashCode() { return Objects.hash(userId, productId); }
}
