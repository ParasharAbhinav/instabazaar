package com.instabazaar.service;

import com.instabazaar.dto.*;
import com.instabazaar.entity.*;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.*;
import com.instabazaar.security.CurrentAccount;
import com.instabazaar.util.Text;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @Transactional(readOnly = true)
public class BuyerService {
    private final WishlistRepository wishlist;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final CatalogService catalog;
    private final CurrentAccount current;
    private final ViewMapper mapper;

    public BuyerService(WishlistRepository wishlist, ReviewRepository reviews, UserRepository users,
                        CatalogService catalog, CurrentAccount current, ViewMapper mapper) {
        this.wishlist = wishlist; this.reviews = reviews; this.users = users;
        this.catalog = catalog; this.current = current; this.mapper = mapper;
    }

    @PreAuthorize("isAuthenticated()")
    public List<Views.ProductView> wishlist() {
        return wishlist.findByUserIdOrderByCreatedAtDesc(current.require().getId()).stream().map(item -> mapper.product(item.getProduct())).toList();
    }

    public boolean saved(Long productId) {
        AppUser user = current.optional();
        return user != null && wishlist.existsByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional @PreAuthorize("isAuthenticated()")
    public void save(Long productId) {
        AppUser user = users.lockById(current.require().getId()).orElseThrow(DomainException::forbidden);
        Product product = catalog.publicEntity(productId);
        if (!wishlist.existsByUserIdAndProductId(user.getId(), productId)) {
            WishlistItem item = new WishlistItem(); item.user = user; item.product = product; wishlist.saveAndFlush(item);
        }
    }

    @Transactional @PreAuthorize("isAuthenticated()")
    public void remove(Long productId) {
        wishlist.findByUserIdAndProductId(current.require().getId(), productId).ifPresent(wishlist::delete);
    }

    public List<Views.ReviewView> reviews(Long productId) {
        catalog.publicEntity(productId);
        return reviews.findByProductIdAndHiddenFalseAndUserActiveTrueOrderByCreatedAtDesc(productId).stream().map(mapper::review).toList();
    }

    @Transactional @PreAuthorize("isAuthenticated()")
    public void review(Long productId, @Valid Forms.ReviewInput form) {
        AppUser user = users.lockById(current.require().getId()).orElseThrow(DomainException::forbidden);
        Product product = catalog.publicEntity(productId);
        if (product.getSeller().getUser().getId().equals(user.getId())) throw DomainException.invalid("You cannot review your own product.");
        if (reviews.findByUserIdAndProductId(user.getId(), productId).isPresent()) {
            throw DomainException.invalid("You already reviewed this product. Remove your existing review before posting another.");
        }
        Review review = new Review(); review.user = user; review.product = product;
        review.rating = form.rating(); review.text = Text.clean(form.text()); reviews.saveAndFlush(review);
    }

    @Transactional @PreAuthorize("isAuthenticated()")
    public void removeReview(Long id) {
        Review review = reviews.findById(id).orElseThrow(DomainException::missing);
        if (!review.getUser().getId().equals(current.require().getId())) throw DomainException.forbidden();
        reviews.delete(review);
    }
}
