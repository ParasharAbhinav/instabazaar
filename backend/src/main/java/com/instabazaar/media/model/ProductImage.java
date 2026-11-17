package com.instabazaar.media.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.BaseEntity;
import com.instabazaar.catalog.model.Product;
import com.instabazaar.media.model.MediaAsset;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "product_image")
public class ProductImage extends BaseEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, unique = true, updatable = false)
    private MediaAsset asset;

    @Min(0)
    @Max(9)
    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @NotNull
    @Size(max = 160)
    @Column(name = "alt_text", nullable = false, length = 160)
    private String altText;

    protected ProductImage() {}

    public ProductImage(Product product, MediaAsset asset, short displayOrder, String altText) {
        this.product = Objects.requireNonNull(product, "product");
        this.asset = Objects.requireNonNull(asset, "asset");
        this.displayOrder = displayOrder;
        this.altText = Objects.requireNonNull(altText, "altText");
    }

    public Product getProduct() { return product; }

    public MediaAsset getAsset() { return asset; }

    public short getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(short displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getAltText() { return altText; }
    public void setAltText(String altText) {
        this.altText = Objects.requireNonNull(altText, "altText");
    }

}
