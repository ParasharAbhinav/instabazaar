package com.instabazaar.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
@Embeddable
public class ProductSizeId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "product_id", nullable = false)
    @NotNull
    private UUID productId;

    @Column(name = "size_code", nullable = false, length = 20)
    @NotBlank
    @Size(max = 20)
    private String sizeCode;

    protected ProductSizeId() {}
    public ProductSizeId(UUID productId, String sizeCode) {
        this.productId = Objects.requireNonNull(productId, "productId");
        this.sizeCode = Objects.requireNonNull(sizeCode, "sizeCode");
    }
    public UUID getProductId() { return productId; }
    public String getSizeCode() { return sizeCode; }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProductSizeId that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(sizeCode, that.sizeCode);
    }
    @Override
    public int hashCode() { return Objects.hash(productId, sizeCode); }
}
