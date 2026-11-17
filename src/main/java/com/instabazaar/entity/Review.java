package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class Review extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Product product;
    @Column(nullable = false)
    public int rating;
    @Column(nullable = false, length = 1500)
    public String text = "";
    @Column(nullable = false)
    public boolean hidden;

    public AppUser getUser() { return user; }
    public Product getProduct() { return product; }
    public int getRating() { return rating; }
    public String getText() { return text; }
    public boolean getHidden() { return hidden; }
}
