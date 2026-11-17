package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "wishlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class WishlistItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Product product;

    public AppUser getUser() { return user; }
    public Product getProduct() { return product; }
}
