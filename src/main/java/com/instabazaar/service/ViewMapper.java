package com.instabazaar.service;

import com.instabazaar.dto.Views;
import com.instabazaar.entity.*;
import com.instabazaar.enums.ListingStatus;
import com.instabazaar.repository.ReviewRepository;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ViewMapper {
    private final ReviewRepository reviews;
    public ViewMapper(ReviewRepository reviews) { this.reviews = reviews; }

    public boolean visible(Product product) {
        return product.getStatus() == ListingStatus.ACTIVE && !product.getHidden() && product.getSeller().getUser().getActive();
    }

    public Views.ProductView product(Product product) {
        List<Review> ratings = reviews.findByProductIdAndHiddenFalseAndUserActiveTrueOrderByCreatedAtDesc(product.getId());
        List<String> images = product.getImages().stream().map(image -> "/media/" + image.getStorageKey()).toList();
        if (images.isEmpty()) images = List.of("/images/" + (product.getIllustration() == null ? "product-placeholder.svg" : product.getIllustration()));
        return new Views.ProductView(product.getId(), product.getVersion(), product.getTitle(), product.getDescription(), product.getPrice(),
                product.getCategory().getId(), product.getCategory().getName(), product.getColour(), product.getSizes(), product.getMaterial(),
                product.getOccasion(), product.getBrand(), product.getStock(), product.getStatus(), product.getHidden(), product.getDemo(),
                visible(product) && product.getStock() > 0, visible(product), product.getSourceUrl(), product.getCaption(),
                product.getSeller().getId(), product.getSeller().getStoreName(), product.getTags().stream().map(tag -> tag.getLabel()).sorted().toList(),
                images, ratings.stream().mapToInt(review -> review.getRating()).average().orElse(0), ratings.size());
    }

    public Views.SellerView seller(SellerProfile seller) {
        List<Review> ratings = reviews.findByProductSellerIdAndHiddenFalseAndUserActiveTrue(seller.getId()).stream()
                .filter(review -> visible(review.getProduct())).toList();
        return new Views.SellerView(seller.getId(), seller.getStoreName(), seller.getDescription(), seller.getPublicEmail(),
                seller.getWhatsappUrl(), seller.getInstagramUrl(), seller.getFacebookUrl(),
                seller.getLogoKey() == null ? null : "/media/" + seller.getLogoKey(), seller.getUser().getDemo(),
                ratings.stream().mapToInt(review -> review.getRating()).average().orElse(0), ratings.size());
    }

    public Views.ReviewView review(Review review) {
        return new Views.ReviewView(review.getId(), review.getProduct().getId(), review.getProduct().getTitle(), review.getUser().getId(),
                review.getUser().getDisplayName(), review.getRating(), review.getText(), review.getHidden(),
                review.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate());
    }
}
