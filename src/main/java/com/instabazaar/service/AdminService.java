package com.instabazaar.service;

import com.instabazaar.dto.Views;
import com.instabazaar.enums.Role;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.*;
import com.instabazaar.security.CurrentAccount;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
public class AdminService {
    private final UserRepository users;
    private final SellerRepository sellers;
    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final ViewMapper mapper;
    private final CurrentAccount current;

    public AdminService(UserRepository users, SellerRepository sellers, ProductRepository products,
                        ReviewRepository reviews, ViewMapper mapper, CurrentAccount current) {
        this.users = users; this.sellers = sellers; this.products = products;
        this.reviews = reviews; this.mapper = mapper; this.current = current;
    }

    public Views.Stats stats() { return new Views.Stats(users.count(), sellers.count(), products.count(), reviews.count()); }
    public List<Views.UserView> users(int page) {
        return users.findAll(paging(page)).stream().map(user -> new Views.UserView(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.getActive(), user.getDemo())).toList();
    }
    public List<Views.ProductView> products(int page) { return products.findAll(paging(page)).stream().map(mapper::product).toList(); }
    public List<Views.ReviewView> reviews(int page) { return reviews.findAll(paging(page)).stream().map(mapper::review).toList(); }

    @Transactional public void setUserActive(Long id, boolean active) {
        var user = users.findById(id).orElseThrow(DomainException::missing);
        if (user.getId().equals(current.require().getId()) || user.getRole() == Role.ADMIN) throw DomainException.invalid("Administrator accounts cannot be deactivated here.");
        user.active = active;
    }
    @Transactional public void setProductHidden(Long id, boolean hidden) {
        products.findById(id).orElseThrow(DomainException::missing).hidden = hidden;
    }
    @Transactional public void setReviewHidden(Long id, boolean hidden) {
        reviews.findById(id).orElseThrow(DomainException::missing).hidden = hidden;
    }
    private PageRequest paging(int page) {
        if (page < 0 || page > 100000) throw DomainException.invalid("Invalid page number.");
        return PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"));
    }
}
