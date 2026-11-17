package com.instabazaar.wishlist.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Persistable;
import com.instabazaar.account.model.User;
import com.instabazaar.catalog.model.Product;
@Entity
@Table(name = "wishlist_item")
@Access(AccessType.FIELD)
public class WishlistItem implements Persistable<WishlistItemId> {
    @Transient
    private boolean newEntity = true;
    @EmbeddedId @Valid @NotNull
    private WishlistItemId id;
    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;
    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;
    protected WishlistItem() {}
    public WishlistItem(User user, Product product) {
        this.user = Objects.requireNonNull(user, "user");
        this.product = Objects.requireNonNull(product, "product");
        this.id = new WishlistItemId(user.getId(), product.getId());
    }
    @PrePersist
    protected void initializeCreatedAt() { createdAt = Instant.now(); }
    public WishlistItemId getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostPersist
    @PostLoad
    protected void markPersisted() { newEntity = false; }
    public User getUser() { return user; }
    public Product getProduct() { return product; }
    public Instant getCreatedAt() { return createdAt; }
}
