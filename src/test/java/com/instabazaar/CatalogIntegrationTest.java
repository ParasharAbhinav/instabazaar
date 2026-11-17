package com.instabazaar;

import com.instabazaar.dto.*;
import com.instabazaar.enums.ListingStatus;
import com.instabazaar.exception.DomainException;
import com.instabazaar.security.AccountPrincipal;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogIntegrationTest extends ApplicationTestSupport {
    @Test void keywordSearchUnderstandsRememberedDetailsAndSellerNames() {
        assertThat(search.search(query("red dress for a party")).products()).extracting(Views.ProductView::id).containsExactly(productId);
        assertThat(search.search(query("Riya Fashion Studio")).products()).hasSize(1);
        assertThat(search.search(query("evening")).products()).hasSize(1);
        assertThat(search.search(query("blue jacket")).products()).isEmpty();
    }

    @Test void searchCombinesFiltersAndDoesNotConfuseSizes() {
        Long category = categories.findByName("Dresses").orElseThrow().getId();
        var filters = new SearchQuery("dress", category, null, new BigDecimal("1400"), new BigDecimal("1500"), "red", "M", true, "price-asc", 0);
        assertThat(search.search(filters).products()).hasSize(1);
        assertThat(search.search(new SearchQuery("", null, null, null, null, "", "XS", null, "relevance", 0)).products()).isEmpty();
        assertThat(search.search(new SearchQuery("", null, null, null, new BigDecimal("1000"), "", "", null, "relevance", 0)).products()).isEmpty();
    }

    @Test void priceSortingAndPaginationAreStable() {
        authenticate(seller);
        for (int index = 0; index < 13; index++) catalog.save(null, input("Dress " + index, String.valueOf(2000 + index), index), null);
        SecurityContextHolder.clearContext();
        var first = search.search(new SearchQuery("", null, null, null, null, "", "", null, "price-asc", 0));
        var second = search.search(new SearchQuery("", null, null, null, null, "", "", null, "price-asc", 1));
        assertThat(first.total()).isEqualTo(14); assertThat(first.pageCount()).isEqualTo(2);
        assertThat(first.products()).hasSize(12); assertThat(second.products()).hasSize(2);
        assertThat(first.products()).extracting(Views.ProductView::price).isSorted();
        assertThat(first.products().getFirst().id()).isEqualTo(productId);
        assertThat(second.products()).extracting(Views.ProductView::id).doesNotContainAnyElementsOf(first.products().stream().map(Views.ProductView::id).toList());
        var desc = search.search(new SearchQuery("", null, null, null, null, "", "", null, "price-desc", 0));
        assertThat(desc.products()).extracting(Views.ProductView::price).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test void titleMatchesRankAboveDescriptionMatches() {
        authenticate(seller);
        Long other = catalog.save(null, input("Another garment", "100.00", 1), null);
        SecurityContextHolder.clearContext();
        assertThat(search.search(query("red dress")).products()).extracting(Views.ProductView::id).startsWith(productId).contains(other);
    }

    @Test void archivedAndHiddenProductsAreNotPublicButWishlistRemainsRemovable() {
        authenticate(buyer); buyers.save(productId);
        authenticate(seller); catalog.archive(productId);
        SecurityContextHolder.clearContext();
        assertThat(search.search(query("dress")).products()).isEmpty();
        assertThatThrownBy(() -> catalog.product(productId)).isInstanceOf(DomainException.class);
        authenticate(buyer); assertThat(buyers.wishlist()).hasSize(1); assertThat(buyers.wishlist().getFirst().visible()).isFalse();
        buyers.remove(productId); assertThat(buyers.wishlist()).isEmpty();
    }

    @Test void inactiveSellersDisappearFromProductsAndStores() {
        authenticate(administrator); adminService.setUserActive(seller.getId(), false);
        SecurityContextHolder.clearContext();
        assertThat(search.search(query("dress")).products()).isEmpty();
        assertThat(catalog.sellers("Riya")).isEmpty();
        assertThatThrownBy(() -> catalog.seller(sellers.findByUserId(seller.getId()).orElseThrow().getId())).isInstanceOf(DomainException.class);
    }

    @Test void reviewsCalculateRealRatingsAndModerationChangesAggregates() {
        authenticate(buyer); buyers.review(productId, new Forms.ReviewInput(4, "Beautiful fabric"));
        authenticate(otherSeller); buyers.review(productId, new Forms.ReviewInput(2, "Sizing could be clearer"));
        assertThat(catalog.product(productId).rating()).isEqualTo(3);
        assertThat(catalog.product(productId).reviewCount()).isEqualTo(2);
        Long hiddenReview = reviews.findByUserIdAndProductId(otherSeller.getId(), productId).orElseThrow().getId();
        authenticate(administrator); adminService.setReviewHidden(hiddenReview, true);
        assertThat(catalog.product(productId).rating()).isEqualTo(4);
        assertThat(catalog.product(productId).reviewCount()).isEqualTo(1);
    }

    @Test void reviewRemovalRequiresOwnership() {
        authenticate(buyer); buyers.review(productId, new Forms.ReviewInput(4, "Honest review"));
        Long reviewId = reviews.findByUserIdAndProductId(buyer.getId(), productId).orElseThrow().getId();
        authenticate(otherSeller);
        assertThatThrownBy(() -> buyers.removeReview(reviewId)).isInstanceOf(DomainException.class);
    }

    @Test void productUpdatesPreserveModerationAndRejectStaleVersions() {
        authenticate(administrator); adminService.setProductHidden(productId, true);
        products.flush();
        authenticate(seller);
        var original = catalog.editor(productId);
        catalog.save(productId, original, null);
        assertThat(catalog.ownProduct(productId).hidden()).isTrue();
        var stale = new Forms.ProductInput(original.title(), original.description(), original.price(), original.categoryId(), original.colour(), original.sizes(), original.material(), original.occasion(), original.brand(), original.stock(), original.status(), original.sourceUrl(), original.caption(), original.tags(), 999L);
        assertThatThrownBy(() -> catalog.save(productId, stale, null)).isInstanceOf(DomainException.class);
    }

    @Test void realImagesAreStoredBehindPermissionChecks() throws Exception {
        authenticate(seller);
        Long id = catalog.save(null, input("Uploaded dress", "999.00", 1), png());
        String url = catalog.ownProduct(id).imageUrl();
        assertThat(url).startsWith("/media/").doesNotContain("escape");
        SecurityContextHolder.clearContext();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        authenticate(administrator); adminService.setProductHidden(id, true);
        SecurityContextHolder.clearContext();
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(url).with(user(AccountPrincipal.from(seller)))).andExpect(status().isOk());
    }

    @Test void sellerCanRemoveOnlyTheirOwnImages() throws Exception {
        authenticate(seller);
        Long id = catalog.save(null, input("Image dress", "999.00", 1), png());
        String key = catalog.ownProduct(id).imageUrl().substring("/media/".length());
        authenticate(otherSeller);
        assertThatThrownBy(() -> catalog.removeImage(id, key)).isInstanceOf(DomainException.class);
        authenticate(seller); catalog.removeImage(id, key); products.flush();
        assertThat(catalog.ownProduct(id).imageUrl()).isEqualTo("/images/product-placeholder.svg");
    }

    @Test void externalContactLinksHaveAnExplicitNotice() throws Exception {
        authenticate(seller);
        catalog.updateStore(new Forms.Store("Riya Fashion Studio", "Independent dresses", "hello@example.test", "https://wa.me/919876543210", "https://www.instagram.com/riya/", ""), null);
        Long storeId = catalog.ownStore().id();
        SecurityContextHolder.clearContext();
        mvc.perform(get("/sellers/{id}/contact/whatsapp", storeId)).andExpect(status().isOk());
    }

    @Test void invalidExternalLinksDoNotModifyTheStore() {
        authenticate(seller);
        assertThatThrownBy(() -> catalog.updateStore(new Forms.Store("Hijacked", "description", "", "https://wa.me.attacker.example/123", "", ""), null))
                .isInstanceOf(DomainException.class);
        assertThat(catalog.ownStore().storeName()).isEqualTo("Riya Fashion Studio");
    }

    private SearchQuery query(String text) { return new SearchQuery(text, null, null, null, null, "", "", null, "relevance", 0); }

    @Test void imageLimitIsEnforcedAndExistingImagesRemainIntact() throws Exception {
        authenticate(seller);
        for (int index = 0; index < 5; index++) catalog.save(productId, catalog.editor(productId), png());
        assertThat(catalog.ownProduct(productId).imageUrls()).hasSize(5);
        assertThatThrownBy(() -> catalog.save(productId, catalog.editor(productId), png())).isInstanceOf(DomainException.class);
        assertThat(catalog.ownProduct(productId).imageUrls()).hasSize(5);
    }

    @Test void stockAndDraftStatusControlAvailabilityAndDiscoverability() {
        authenticate(seller);
        Long id = catalog.save(null, input("Out of stock dress", "999.00", 0), null);
        assertThat(catalog.product(id).available()).isFalse();
        assertThat(search.search(new SearchQuery("", null, null, null, null, "", "", false, "newest", 0)).products())
                .extracting(Views.ProductView::id).containsExactly(id);
        var draft = catalog.editor(id);
        catalog.save(id, new Forms.ProductInput(draft.title(), draft.description(), draft.price(), draft.categoryId(), draft.colour(), draft.sizes(), draft.material(), draft.occasion(), draft.brand(), draft.stock(), ListingStatus.DRAFT, draft.sourceUrl(), draft.caption(), draft.tags(), draft.version()), null);
        assertThat(search.search(query("stock")).products()).isEmpty();
    }
    private MockMultipartFile png() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("image", "../../escape.png", "image/png", output.toByteArray());
    }
}
