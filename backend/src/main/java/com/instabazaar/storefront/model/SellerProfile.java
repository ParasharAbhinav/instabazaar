package com.instabazaar.storefront.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.Objects;
import com.instabazaar.shared.model.VersionedEntity;
import com.instabazaar.account.model.User;
import com.instabazaar.media.model.MediaAsset;
/** Persistence model only; expose dedicated DTOs from future APIs. */
@Entity
@Table(name = "seller_profile")
public class SellerProfile extends VersionedEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @NotBlank
    @Size(max = 120)
    @Column(name = "store_name", nullable = false, length = 120)
    private String storeName;

    @NotNull
    @Size(max = 2000)
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "logo_asset_id", nullable = true)
    private MediaAsset logoAsset;

    @Size(max = 254)
    @Email
    @Column(name = "public_email", nullable = true, length = 254)
    private String publicEmail;

    @Size(max = 20)
    @Pattern(regexp = "\\+[1-9][0-9]{1,14}")
    @Column(name = "public_phone", nullable = true, length = 20)
    private String publicPhone;

    @Size(max = 20)
    @Pattern(regexp = "\\+[1-9][0-9]{1,14}")
    @Column(name = "whatsapp_number", nullable = true, length = 20)
    private String whatsappNumber;

    @Size(max = 2048)
    @Column(name = "instagram_url", nullable = true, length = 2048)
    private String instagramUrl;

    @Size(max = 2048)
    @Column(name = "facebook_url", nullable = true, length = 2048)
    private String facebookUrl;

    protected SellerProfile() {}

    public SellerProfile(User user, String storeName, String description) {
        this.user = Objects.requireNonNull(user, "user");
        this.storeName = Objects.requireNonNull(storeName, "storeName");
        this.description = Objects.requireNonNull(description, "description");
    }

    public User getUser() { return user; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) {
        this.storeName = Objects.requireNonNull(storeName, "storeName");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description");
    }

    public MediaAsset getLogoAsset() { return logoAsset; }
    public void setLogoAsset(MediaAsset logoAsset) {
        this.logoAsset = logoAsset;
    }

    public String getPublicEmail() { return publicEmail; }
    public void setPublicEmail(String publicEmail) {
        this.publicEmail = publicEmail;
    }

    public String getPublicPhone() { return publicPhone; }
    public void setPublicPhone(String publicPhone) {
        this.publicPhone = publicPhone;
    }

    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getFacebookUrl() { return facebookUrl; }
    public void setFacebookUrl(String facebookUrl) {
        this.facebookUrl = facebookUrl;
    }

}
