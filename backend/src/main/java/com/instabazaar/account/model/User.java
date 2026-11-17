package com.instabazaar.account.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.VersionedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "app_user")
public class User extends VersionedEntity {
    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotNull
    @Size(max = 254)
    @Email
    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected User() {}

    public User(String name, String email, String passwordHash, Role role) {
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
    }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = Objects.requireNonNull(email, "email");
    }

    @JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role, "role");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
