package com.instabazaar.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.VersionedEntity;
import com.instabazaar.storefront.model.SellerProfile;
import com.instabazaar.catalog.model.Category;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "product")
public class Product extends VersionedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private SellerProfile seller;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotBlank
    @Size(max = 160)
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @NotNull
    @Size(max = 5000)
    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @Pattern(regexp = "INR")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)", length = 3)
    private String currency = "INR";

    @NotBlank
    @Size(max = 40)
    @Column(name = "colour", nullable = false, length = 40)
    private String colour;

    @Size(max = 100)
    @Column(name = "material", nullable = true, length = 100)
    private String material;

    @Size(max = 100)
    @Column(name = "occasion", nullable = true, length = 100)
    private String occasion;

    @Size(max = 100)
    @Column(name = "brand", nullable = true, length = 100)
    private String brand;

    @Min(0)
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "listing_status", nullable = false, length = 10)
    private ListingStatus listingStatus = ListingStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 10)
    private ModerationStatus moderationStatus = ModerationStatus.VISIBLE;

    @Size(max = 2048)
    @Column(name = "source_url", nullable = true, length = 2048)
    private String sourceUrl;

    @Size(max = 2000)
    @Column(name = "reel_caption", length = 2000)
    private String reelCaption;

    public String getReelCaption() { return reelCaption; }
    public void setReelCaption(String reelCaption) { this.reelCaption = reelCaption; }

    protected Product() {}

    public Product(SellerProfile seller, Category category, String title, String description, BigDecimal price, String colour) {
        this.seller = Objects.requireNonNull(seller, "seller");
        this.category = Objects.requireNonNull(category, "category");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.price = Objects.requireNonNull(price, "price");
        this.colour = Objects.requireNonNull(colour, "colour");
    }

    public SellerProfile getSeller() { return seller; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) {
        this.category = Objects.requireNonNull(category, "category");
    }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description");
    }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) {
        this.price = Objects.requireNonNull(price, "price");
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) {
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public String getColour() { return colour; }
    public void setColour(String colour) {
        this.colour = Objects.requireNonNull(colour, "colour");
    }

    public String getMaterial() { return material; }
    public void setMaterial(String material) {
        this.material = material;
    }

    public String getOccasion() { return occasion; }
    public void setOccasion(String occasion) {
        this.occasion = occasion;
    }

    public String getBrand() { return brand; }
    public void setBrand(String brand) {
        this.brand = brand;
    }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) {
        this.available = available;
    }

    public ListingStatus getListingStatus() { return listingStatus; }
    public void setListingStatus(ListingStatus listingStatus) {
        this.listingStatus = Objects.requireNonNull(listingStatus, "listingStatus");
    }

    public ModerationStatus getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(ModerationStatus moderationStatus) {
        this.moderationStatus = Objects.requireNonNull(moderationStatus, "moderationStatus");
    }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

}
