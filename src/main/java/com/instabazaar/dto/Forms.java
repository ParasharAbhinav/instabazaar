package com.instabazaar.dto;

import com.instabazaar.enums.ListingStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Forms {
    private Forms() {}

    public record Registration(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Pattern(regexp = "BUYER|SELLER", message = "Choose buyer or seller.") String role) {
        public Registration { displayName = com.instabazaar.util.Text.clean(displayName); email = com.instabazaar.util.Text.clean(email); }
        public static Registration empty() { return new Registration("", "", "", "BUYER"); }
    }

    public record Profile(@NotBlank @Size(max = 80) String displayName,
                          @NotBlank @Email @Size(max = 190) String email) {}

    public record Store(@NotBlank @Size(max = 100) String storeName,
                        @NotBlank @Size(max = 2000) String description,
                        @Email @Size(max = 190) String publicEmail,
                        @Size(max = 500) String whatsappUrl,
                        @Size(max = 500) String instagramUrl,
                        @Size(max = 500) String facebookUrl) {}

    public record ProductInput(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 50) String colour,
            @NotBlank @Size(max = 120) String sizes,
            @Size(max = 100) String material,
            @Size(max = 100) String occasion,
            @Size(max = 100) String brand,
            @NotNull @Min(0) @Max(1000000) Integer stock,
            @NotNull ListingStatus status,
            @Size(max = 500) String sourceUrl,
            @Size(max = 2000) String caption,
            @Size(max = 1000) String tags,
            @NotNull @Min(0) Long version) {
        public static ProductInput empty() {
            return new ProductInput("", "", null, null, "", "", "", "", "", 1, ListingStatus.ACTIVE, "", "", "", 0L);
        }
    }

    public record ReviewInput(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1500) String text) {}
}
