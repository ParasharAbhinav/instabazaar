package com.instabazaar.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.CreatedEntity;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "product_tag")
public class ProductTag extends CreatedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @NotBlank
    @Size(max = 60)
    @Column(name = "tag", nullable = false, length = 60)
    private String tag;

    // PostgreSQL owns case conversion, so its collation and uniqueness agree.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_tag", nullable = false, insertable = false,
            updatable = false, columnDefinition = "text")
    private String normalizedTag;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private TagSource source;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TagStatus status;

    protected ProductTag() {}

    public ProductTag(Product product, String tag, TagSource source, TagStatus status) {
        this.product = Objects.requireNonNull(product, "product");
        setTag(tag);
        this.source = Objects.requireNonNull(source, "source");
        this.status = Objects.requireNonNull(status, "status");
    }

    public Product getProduct() { return product; }

    public String getTag() { return tag; }
    public void setTag(String tag) {
        this.tag = Objects.requireNonNull(tag, "tag").strip();
    }

    public String getNormalizedTag() { return normalizedTag; }

    public TagSource getSource() { return source; }
    public void setSource(TagSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public TagStatus getStatus() { return status; }
    public void setStatus(TagStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

}
