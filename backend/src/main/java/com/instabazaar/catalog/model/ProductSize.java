package com.instabazaar.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import org.springframework.data.domain.Persistable;
@Entity
@Table(name = "product_size")
@Access(AccessType.FIELD)
public class ProductSize implements Persistable<ProductSizeId> {
    @Transient
    private boolean newEntity = true;
    @EmbeddedId @Valid @NotNull
    private ProductSizeId id;
    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;
    protected ProductSize() {}
    public ProductSize(Product product, String sizeCode) {
        this.product = Objects.requireNonNull(product, "product");
        this.id = new ProductSizeId(product.getId(), sizeCode);
    }
    public ProductSizeId getId() { return id; }
    @Override
    public boolean isNew() { return newEntity; }
    @PostPersist
    @PostLoad
    protected void markPersisted() { newEntity = false; }
    public Product getProduct() { return product; }
    public String getSizeCode() { return id.getSizeCode(); }
}
