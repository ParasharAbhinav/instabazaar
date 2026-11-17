package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "product_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "label"}))
public class ProductTag extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Product product;
    @Column(nullable = false, length = 50)
    public String label;
    @Column(nullable = false)
    public boolean suggested;

    public Product getProduct() { return product; }
    public String getLabel() { return label; }
    public boolean getSuggested() { return suggested; }
}
