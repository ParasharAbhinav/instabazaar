package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "product_images")
public class ProductImage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Product product;
    @Column(nullable = false, unique = true, length = 100)
    public String storageKey;
    @Column(nullable = false, length = 40)
    public String contentType;

    public Product getProduct() { return product; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
}
