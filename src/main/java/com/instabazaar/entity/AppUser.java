package com.instabazaar.entity;

import com.instabazaar.enums.Role;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "app_users")
public class AppUser extends BaseEntity {
    @Column(nullable = false, length = 80)
    public String displayName;
    @Column(nullable = false, unique = true, length = 190)
    public String email;
    @Column(nullable = false, length = 100)
    public String passwordHash;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 16)
    public Role role;
    @Column(nullable = false)
    public boolean active = true;
    @Column(nullable = false)
    public boolean demo;

    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean getActive() { return active; }
    public boolean getDemo() { return demo; }
}
