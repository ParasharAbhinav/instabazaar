package com.instabazaar.dto;

import com.instabazaar.enums.ListingStatus;
import com.instabazaar.enums.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Views {
    private Views() {}

    public record CategoryView(Long id, String name) {}
    public record UserView(Long id, String displayName, String email, Role role, boolean active, boolean demo) {}
    public record SellerView(Long id, String storeName, String description, String publicEmail,
                             String whatsappUrl, String instagramUrl, String facebookUrl,
                             String logoUrl, boolean demo, double rating, int reviewCount) {}
    public record ProductView(Long id, long version, String title, String description, BigDecimal price,
                              Long categoryId, String categoryName, String colour, String sizes,
                              String material, String occasion, String brand, int stock, ListingStatus status,
                              boolean hidden, boolean demo, boolean available, boolean visible,
                              String sourceUrl, String caption, Long sellerId, String storeName,
                              List<String> tags, List<String> imageUrls, double rating, int reviewCount) {
        public String imageUrl() { return imageUrls.getFirst(); }
    }
    public record ReviewView(Long id, Long productId, String productTitle, Long userId, String displayName,
                             int rating, String text, boolean hidden, LocalDate createdDate) {}
    public record SearchResults(List<ProductView> products, long total, int page, int pageCount) {}
    public record Stats(long users, long sellers, long products, long reviews) {}
}
