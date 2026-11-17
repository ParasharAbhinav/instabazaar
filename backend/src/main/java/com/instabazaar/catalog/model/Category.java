package com.instabazaar.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.AuditedEntity;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "category")
public class Category extends AuditedEntity {
    @NotBlank
    @Size(max = 80)
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
    @Column(name = "slug", nullable = false, length = 100, unique = true)
    private String slug;

    @Size(max = 500)
    @Column(name = "description", nullable = true, length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Category() {}

    public Category(String name, String slug) {
        this.name = Objects.requireNonNull(name, "name");
        this.slug = Objects.requireNonNull(slug, "slug");
    }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) {
        this.slug = Objects.requireNonNull(slug, "slug");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) {
        this.active = active;
    }

}
