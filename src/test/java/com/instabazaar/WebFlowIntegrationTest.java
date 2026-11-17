package com.instabazaar;

import com.instabazaar.enums.Role;
import com.instabazaar.security.AccountPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebFlowIntegrationTest extends ApplicationTestSupport {
    @Test void publicPagesAndAssetsRender() throws Exception {
        for (String route : List.of("/", "/products", "/products?q=red+dress+for+a+party", "/products?q=nothingmatches", "/sellers", "/login", "/register", "/register?role=SELLER", "/css/app.css", "/js/app.js", "/vendor/bootstrap.min.css", "/images/hero.svg", "/images/icons.svg")) {
            mvc.perform(get(route)).andExpect(status().isOk());
        }
        mvc.perform(get("/products/{id}", productId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Red embroidered party dress")))
                .andExpect(content().string(not(containsString("Private seller caption"))))
                .andExpect(content().string(not(containsString("Not quite found it?"))));
        mvc.perform(get("/sellers/{id}", sellers.findByUserId(seller.getId()).orElseThrow().getId())).andExpect(status().isOk());
        mvc.perform(get("/categories/{id}", categories.findByName("Dresses").orElseThrow().getId())).andExpect(status().is3xxRedirection());
    }

    @Test void registrationHashesPasswordsAndCannotEscalateRole() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("displayName", "New Buyer").param("email", " NEW@example.test ")
                        .param("password", PASSWORD).param("role", "BUYER").param("active", "false"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered"));
        var registered = users.findByEmail("new@example.test").orElseThrow();
        assertThat(registered.getRole()).isEqualTo(Role.BUYER); assertThat(registered.getActive()).isTrue();
        assertThat(registered.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, registered.getPasswordHash())).isTrue();
        mvc.perform(post("/register").with(csrf()).param("displayName", "Escalation").param("email", "evil@example.test")
                .param("password", PASSWORD).param("role", "ADMIN")).andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertThat(users.existsByEmail("evil@example.test")).isFalse();
    }

    @Test void registrationValidatesDuplicatesAndPasswords() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("displayName", "Duplicate").param("email", buyer.getEmail())
                .param("password", PASSWORD).param("role", "BUYER")).andExpect(model().attributeHasErrors("form"));
        mvc.perform(post("/register").with(csrf()).param("displayName", "Short").param("email", "short@example.test")
                .param("password", "tiny").param("role", "BUYER")).andExpect(model().attributeHasErrors("form"));
    }

    @Test void sellerRegistrationCreatesAStore() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("displayName", "Boutique").param("email", "boutique@example.test")
                .param("password", PASSWORD).param("role", "SELLER")).andExpect(status().is3xxRedirection());
        var registered = users.findByEmail("boutique@example.test").orElseThrow();
        assertThat(sellers.findByUserId(registered.getId())).isPresent();
    }

    @Test void loginLogoutAndCsrfProtectionWork() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", buyer.getEmail()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(buyer.getEmail()));
        mvc.perform(post("/login").with(csrf()).param("email", buyer.getEmail()).param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(post("/wishlist/{id}", productId).with(user(AccountPrincipal.from(buyer)))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user(AccountPrincipal.from(buyer))).with(csrf())).andExpect(unauthenticated());
        mvc.perform(get("/wishlist")).andExpect(status().is3xxRedirection());
    }

    @Test void buyerCannotAccessSellerOrAdminRoutes() throws Exception {
        for (String route : List.of("/seller/dashboard", "/seller/products/new", "/admin", "/admin/users")) {
            mvc.perform(get(route).with(user(AccountPrincipal.from(buyer)))).andExpect(status().isForbidden());
        }
    }

    @Test void authenticatedWorkspacePagesRender() throws Exception {
        for (String route : List.of("/seller/dashboard", "/seller/products", "/seller/store", "/seller/products/new", "/seller/products/" + productId + "/edit", "/profile", "/wishlist")) {
            mvc.perform(get(route).with(user(AccountPrincipal.from(seller)))).andExpect(status().isOk());
        }
        for (String route : List.of("/admin", "/admin/users", "/admin/products", "/admin/reviews")) {
            mvc.perform(get(route).with(user(AccountPrincipal.from(administrator)))).andExpect(status().isOk());
        }
    }

    @Test void sellerCannotEditOrArchiveAnotherSellersProduct() throws Exception {
        mvc.perform(get("/seller/products/{id}/edit", productId).with(user(AccountPrincipal.from(otherSeller)))).andExpect(status().isForbidden());
        mvc.perform(post("/seller/products/{id}/archive", productId).with(user(AccountPrincipal.from(otherSeller))).with(csrf()).param("confirm", "true"))
                .andExpect(status().isForbidden());
    }

    @Test void invalidProductAndTamperedOwnerAreHandled() throws Exception {
        mvc.perform(multipart("/seller/products").with(user(AccountPrincipal.from(seller))).with(csrf())
                .param("title", "").param("description", "description").param("price", "-4").param("stock", "-1")
                .param("categoryId", categories.findByName("Dresses").orElseThrow().getId().toString()).param("colour", "red")
                .param("sizes", "M").param("status", "ACTIVE").param("version", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        long before = products.count();
        mvc.perform(productRequest(new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes())))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"));
        assertThat(products.count()).isEqualTo(before);
    }

    @Test void wishlistIsPersistentIdempotentAndOwned() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) mvc.perform(post("/wishlist/{id}", productId).with(user(AccountPrincipal.from(buyer))).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(wishlist.findByUserIdOrderByCreatedAtDesc(buyer.getId())).hasSize(1);
        mvc.perform(post("/wishlist/{id}/remove", productId).with(user(AccountPrincipal.from(otherSeller))).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(wishlist.existsByUserIdAndProductId(buyer.getId(), productId)).isTrue();
        mvc.perform(get("/wishlist").with(user(AccountPrincipal.from(buyer)))).andExpect(content().string(containsString("Red embroidered party dress")));
        mvc.perform(post("/wishlist/{id}/remove", productId).with(user(AccountPrincipal.from(buyer))).with(csrf()));
        assertThat(wishlist.existsByUserIdAndProductId(buyer.getId(), productId)).isFalse();
    }

    @Test void reviewsRejectSelfDuplicateAndOutOfRangeRatings() throws Exception {
        mvc.perform(post("/products/{id}/reviews", productId).with(user(AccountPrincipal.from(seller))).with(csrf()).param("rating", "5").param("text", "My own"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/products/{id}/reviews", productId).with(user(AccountPrincipal.from(buyer))).with(csrf()).param("rating", "6"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/products/{id}/reviews", productId).with(user(AccountPrincipal.from(buyer))).with(csrf()).param("rating", "4").param("text", "Lovely fabric"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/products/{id}/reviews", productId).with(user(AccountPrincipal.from(buyer))).with(csrf()).param("rating", "1"))
                .andExpect(status().isBadRequest());
        assertThat(reviews.findByUserIdAndProductId(buyer.getId(), productId).orElseThrow().getRating()).isEqualTo(4);
    }

    @Test void adminActionsRequireConfirmationAndProtectAdministrator() throws Exception {
        mvc.perform(post("/admin/products/{id}/visibility", productId).with(user(AccountPrincipal.from(administrator))).with(csrf()).param("hidden", "true"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/users/{id}/status", administrator.getId()).with(user(AccountPrincipal.from(administrator))).with(csrf()).param("active", "false").param("confirm", "true"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/products/{id}/visibility", productId).with(user(AccountPrincipal.from(administrator))).with(csrf()).param("hidden", "true").param("confirm", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/products/{id}", productId)).andExpect(status().isNotFound());
    }

    @Test void deactivatedAccountsLoseExistingSessionAccess() throws Exception {
        buyer.active = false; users.saveAndFlush(buyer);
        mvc.perform(get("/wishlist").with(user(AccountPrincipal.from(buyer)))).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        mvc.perform(post("/login").with(csrf()).param("email", buyer.getEmail()).param("password", PASSWORD)).andExpect(unauthenticated());
    }

    @Test void searchRejectsBadFiltersAndEscapesMarkup() throws Exception {
        mvc.perform(get("/products").param("minPrice", "1000").param("maxPrice", "100")).andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("sort", "evil")).andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/products").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test void profileEditsCannotEscalateRolesOrClaimDuplicateEmails() throws Exception {
        mvc.perform(post("/profile").with(user(AccountPrincipal.from(buyer))).with(csrf())
                .param("displayName", "Updated Buyer").param("email", "updated@test.example").param("role", "ADMIN").param("active", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(users.findById(buyer.getId()).orElseThrow().getRole()).isEqualTo(Role.BUYER);
        assertThat(users.findById(buyer.getId()).orElseThrow().getActive()).isTrue();
        mvc.perform(post("/profile").with(user(AccountPrincipal.from(buyer))).with(csrf())
                .param("displayName", "Duplicate").param("email", seller.getEmail())).andExpect(model().attributeHasErrors("form"));
    }

    @Test void validProductCreationIgnoresForgedSellerAndModerationFields() throws Exception {
        mvc.perform(productRequest(new MockMultipartFile("image", "", "image/png", new byte[0]))
                .param("sellerId", otherSeller.getId().toString()).param("hidden", "true").param("demo", "true"))
                .andExpect(status().is3xxRedirection());
        var saved = products.findAll().stream().filter(product -> product.getTitle().equals("New cotton top")).findFirst().orElseThrow();
        assertThat(saved.getSeller().getUser().getId()).isEqualTo(seller.getId());
        assertThat(saved.getHidden()).isFalse(); assertThat(saved.getDemo()).isFalse();
    }

    @Test void populatedReviewAndModerationPagesRenderWithEscapedContent() throws Exception {
        mvc.perform(post("/products/{id}/reviews", productId).with(user(AccountPrincipal.from(buyer))).with(csrf())
                .param("rating", "4").param("text", "<script>review</script>")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/products/{id}", productId).with(user(AccountPrincipal.from(buyer))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;review&lt;/script&gt;")));
        mvc.perform(get("/admin/reviews").with(user(AccountPrincipal.from(administrator)))).andExpect(status().isOk());
    }

    @Test void cataloguePaginationPreservesSearchAndFilters() throws Exception {
        authenticate(seller);
        for (int index = 0; index < 12; index++) catalog.save(null, input("Red dress " + index, "1499.00", 1), null);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(get("/products").param("q", "red dress").param("colour", "red").param("size", "M").param("sort", "price-asc"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("colour=red"))).andExpect(content().string(containsString("sort=price-asc")));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder productRequest(MockMultipartFile image) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart("/seller/products").file(image)
                .with(user(AccountPrincipal.from(seller))).with(csrf()).param("title", "New cotton top").param("description", "A fresh cotton top")
                .param("price", "899.00").param("categoryId", categories.findByName("Tops").orElseThrow().getId().toString())
                .param("colour", "white").param("sizes", "M").param("stock", "2").param("status", "ACTIVE").param("version", "0");
    }
}
