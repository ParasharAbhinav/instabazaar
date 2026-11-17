package com.instabazaar.entity;

import jakarta.persistence.*;

@Entity @Table(name = "seller_profiles")
public class SellerProfile extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", unique = true)
    public AppUser user;
    @Column(nullable = false, length = 100)
    public String storeName;
    @Column(nullable = false, length = 2000)
    public String description = "";
    @Column(length = 190)
    public String publicEmail;
    @Column(length = 500)
    public String whatsappUrl;
    @Column(length = 500)
    public String instagramUrl;
    @Column(length = 500)
    public String facebookUrl;
    @Column(length = 100)
    public String logoKey;

    public AppUser getUser() { return user; }
    public String getStoreName() { return storeName; }
    public String getDescription() { return description; }
    public String getPublicEmail() { return publicEmail; }
    public String getWhatsappUrl() { return whatsappUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getLogoKey() { return logoKey; }
}
