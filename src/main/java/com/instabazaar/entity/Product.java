package com.instabazaar.entity;

import com.instabazaar.enums.ListingStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "products")
public class Product extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public SellerProfile seller;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public Category category;
    @Column(nullable = false, length = 150)
    public String title;
    @Column(nullable = false, length = 4000)
    public String description;
    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal price;
    @Column(nullable = false, length = 50)
    public String colour;
    @Column(nullable = false, length = 120)
    public String sizes;
    @Column(length = 100)
    public String material;
    @Column(length = 100)
    public String occasion;
    @Column(length = 100)
    public String brand;
    @Column(nullable = false)
    public int stock;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 16)
    public ListingStatus status = ListingStatus.ACTIVE;
    @Column(nullable = false)
    public boolean hidden;
    @Column(nullable = false)
    public boolean demo;
    @Column(length = 500)
    public String sourceUrl;
    @Column(length = 2000)
    public String caption;
    @Column(length = 80)
    public String illustration;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    public Set<ProductTag> tags = new LinkedHashSet<>();
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC")
    public Set<ProductImage> images = new LinkedHashSet<>();

    public SellerProfile getSeller() { return seller; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getColour() { return colour; }
    public String getSizes() { return sizes; }
    public String getMaterial() { return material; }
    public String getOccasion() { return occasion; }
    public String getBrand() { return brand; }
    public int getStock() { return stock; }
    public ListingStatus getStatus() { return status; }
    public boolean getHidden() { return hidden; }
    public boolean getDemo() { return demo; }
    public String getSourceUrl() { return sourceUrl; }
    public String getCaption() { return caption; }
    public String getIllustration() { return illustration; }
    public Set<ProductTag> getTags() { return tags; }
    public Set<ProductImage> getImages() { return images; }
}
