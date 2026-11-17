package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "categories")
public class Category extends BaseEntity {
    @Column(nullable = false, unique = true, length = 80)
    public String name;

    public String getName() { return name; }
}
