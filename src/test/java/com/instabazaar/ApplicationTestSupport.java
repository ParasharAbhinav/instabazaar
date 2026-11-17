package com.instabazaar;

import com.instabazaar.dto.Forms;
import com.instabazaar.entity.*;
import com.instabazaar.enums.*;
import com.instabazaar.repository.*;
import com.instabazaar.security.AccountPrincipal;
import com.instabazaar.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
public abstract class ApplicationTestSupport {
    protected static final String PASSWORD = "Test-password-2026";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected SellerRepository sellers;
    @Autowired protected ProductRepository products;
    @Autowired protected CategoryRepository categories;
    @Autowired protected WishlistRepository wishlist;
    @Autowired protected ReviewRepository reviews;
    @Autowired protected ImageRepository images;
    @Autowired protected CatalogService catalog;
    @Autowired protected BuyerService buyers;
    @Autowired protected AdminService adminService;
    @Autowired protected SearchService search;
    @Autowired protected AccountService accounts;
    protected AppUser buyer;
    protected AppUser seller;
    protected AppUser otherSeller;
    protected AppUser administrator;
    protected Long productId;

    @BeforeEach void fixtures() {
        SecurityContextHolder.clearContext();
        buyer = account("Buyer", "buyer@test.example", Role.BUYER);
        seller = account("Seller", "seller@test.example", Role.SELLER);
        otherSeller = account("Other Seller", "other@test.example", Role.SELLER);
        administrator = account("Admin", "admin@test.example", Role.ADMIN);
        store(seller, "Riya Fashion Studio"); store(otherSeller, "Another Studio");
        authenticate(seller);
        productId = catalog.save(null, input("Red embroidered party dress", "1499.00", 4), null);
        SecurityContextHolder.clearContext();
    }

    protected AppUser account(String name, String email, Role role) {
        AppUser user = new AppUser(); user.displayName = name; user.email = email; user.passwordHash = HASH; user.role = role;
        return users.saveAndFlush(user);
    }
    protected SellerProfile store(AppUser user, String name) {
        SellerProfile profile = new SellerProfile(); profile.user = user; profile.storeName = name;
        profile.description = "Independent fashion, thoughtfully made.";
        return sellers.saveAndFlush(profile);
    }
    protected Forms.ProductInput input(String title, String price, int stock) {
        return new Forms.ProductInput(title, "A floral embroidered dress for a special evening.", new BigDecimal(price),
                categories.findByName("Dresses").orElseThrow().getId(), "red", "S, M, L", "Cotton", "Party wear", "Riya", stock,
                ListingStatus.ACTIVE, "https://www.instagram.com/reel/example/", "Private seller caption not for public display", "floral, evening", 0L);
    }
    protected void authenticate(AppUser user) {
        AccountPrincipal principal = AccountPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
