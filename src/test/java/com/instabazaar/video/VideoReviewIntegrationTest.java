package com.instabazaar.video;

import com.instabazaar.ApplicationTestSupport;
import com.instabazaar.dto.*;
import com.instabazaar.exception.DomainException;
import com.instabazaar.security.AccountPrincipal;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VideoReviewIntegrationTest extends ApplicationTestSupport {
    @Autowired VideoJobs videos;
    @Autowired VideoJobRepository videoJobs;

    @Test void videoPagesAreSellerOnlyAndReviewOwnershipIsEnforced() throws Exception {
        var job = ready(null);
        mvc.perform(get("/seller/videos/new").with(user(AccountPrincipal.from(seller)))).andExpect(status().isOk());
        mvc.perform(get("/seller/videos/new").with(user(AccountPrincipal.from(buyer)))).andExpect(status().isForbidden());
        mvc.perform(get("/seller/videos/{id}", job.getId()).with(user(AccountPrincipal.from(seller)))).andExpect(status().isOk());
        mvc.perform(get("/seller/videos/{id}", job.getId()).with(user(AccountPrincipal.from(otherSeller)))).andExpect(status().isForbidden());
        mvc.perform(post("/seller/videos/{id}/review", job.getId()).with(user(AccountPrincipal.from(seller))).param("tag", "dress")).andExpect(status().isForbidden());
        mvc.perform(post("/seller/videos/{id}/review", job.getId()).with(user(AccountPrincipal.from(otherSeller))).with(csrf()).param("tag", "dress")).andExpect(status().isForbidden());
    }

    @Test void visualTagsAreNotIndexedUntilSellerApprovalAndProductSave() {
        var job = ready(null); authenticate(seller);
        assertThat(search.search(query("blue dress")).products()).isEmpty();
        videos.review(job.getId(), List.of("dress", "blue"));
        assertThat(search.search(query("blue dress")).products()).isEmpty();
        var form = videos.prefill(Forms.ProductInput.empty(), job.getId(), null);
        assertThat(form.title()).isEqualTo("Blue dress"); assertThat(form.colour()).isEqualTo("blue");
        var completed = new Forms.ProductInput(form.title(), "Seller-provided details", new BigDecimal("1299.00"), form.categoryId(), form.colour(), "S, M", "", "", "", 2, form.status(), "", "", form.tags(), 0L);
        Long saved = catalog.save(null, completed, null, job.getId());
        assertThat(search.search(query("blue dress")).products()).extracting(Views.ProductView::id).containsExactly(saved);
        assertThat(search.search(query("striped")).products()).isEmpty();
        assertThat(videos.get(job.getId()).status()).isEqualTo(VideoJob.Status.APPLIED);
        assertThat(videos.get(job.getId()).productId()).isEqualTo(saved);
    }

    @Test void approvalRejectsInjectedAndEmptyTags() {
        var job = ready(null); authenticate(seller);
        assertThatThrownBy(() -> videos.review(job.getId(), List.of("invented keyword"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> videos.review(job.getId(), List.of())).isInstanceOf(DomainException.class);
        assertThat(videos.get(job.getId()).status()).isEqualTo(VideoJob.Status.READY);
    }

    @Test void unreviewedAndOtherSellersJobsCannotPrefillProducts() {
        var job = ready(null); authenticate(seller);
        assertThatThrownBy(() -> videos.prefill(Forms.ProductInput.empty(), job.getId(), null)).isInstanceOf(DomainException.class);
        videos.review(job.getId(), List.of("dress")); authenticate(otherSeller);
        assertThatThrownBy(() -> videos.prefill(Forms.ProductInput.empty(), job.getId(), null)).isInstanceOf(DomainException.class);
    }

    @Test void approvedExistingListingTagsDoNotOverwriteItsOtherDetails() {
        var job = ready(productId); authenticate(seller);
        videos.review(job.getId(), List.of("blue"));
        var form = videos.prefill(catalog.editor(productId), job.getId(), productId);
        assertThat(form.title()).isEqualTo("Red embroidered party dress"); assertThat(form.colour()).isEqualTo("red");
        assertThat(form.tags()).contains("blue");
        catalog.save(productId, form, null, job.getId());
        assertThat(search.search(query("blue")).products()).extracting(Views.ProductView::id).contains(productId);
        assertThat(videos.get(job.getId()).status()).isEqualTo(VideoJob.Status.APPLIED);
    }

    @Test void reviewRedirectLoadsApprovedDataAndPreservesTheReference() throws Exception {
        var job = ready(null);
        mvc.perform(post("/seller/videos/{id}/review", job.getId()).with(user(AccountPrincipal.from(seller))).with(csrf()).param("tag", "dress", "blue"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/seller/products/new?videoJobId=" + job.getId()));
        mvc.perform(get("/seller/products/new").param("videoJobId", job.getId().toString()).with(user(AccountPrincipal.from(seller))))
                .andExpect(status().isOk()).andExpect(model().attributeExists("videoJobId"));
    }

    @Test void analysisCannotBeSilentlyAppliedToTheWrongProduct() {
        var job = ready(productId); authenticate(seller); videos.review(job.getId(), List.of("dress"));
        assertThatThrownBy(() -> videos.prefill(Forms.ProductInput.empty(), job.getId(), null)).isInstanceOf(DomainException.class);
    }

    @Test void approvalUsesExactLabelsRatherThanSubstrings() throws Exception {
        var job = ready(null);
        job.suggestions = "[{\"group\":\"Colour\",\"label\":\"red\",\"score\":0.8},{\"group\":\"Appearance\",\"label\":\"embroidered\",\"score\":0.8}]";
        videoJobs.saveAndFlush(job); authenticate(seller);
        var reviewed = videos.review(job.getId(), List.of("embroidered"));
        assertThat(reviewed.approved("red")).isFalse(); assertThat(reviewed.approved("embroidered")).isTrue();
        String html = mvc.perform(get("/seller/videos/{id}", job.getId()).with(user(AccountPrincipal.from(seller))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var red = java.util.regex.Pattern.compile("<input[^>]*value=\"red\"[^>]*>").matcher(html);
        assertThat(red.find()).isTrue(); assertThat(red.group()).doesNotContain("checked");
    }

    private VideoJob ready(Long target) {
        VideoJob job = new VideoJob(); job.owner = seller; job.product = target == null ? null : products.findById(target).orElseThrow();
        job.storageKey = UUID.randomUUID().toString(); job.status = VideoJob.Status.READY;
        job.suggestions = "[{\"group\":\"Product type\",\"label\":\"dress\",\"score\":0.9},{\"group\":\"Colour\",\"label\":\"blue\",\"score\":0.8},{\"group\":\"Appearance\",\"label\":\"striped\",\"score\":0.7}]";
        job.frameCount = 4; job.duration = 3;
        return videoJobs.saveAndFlush(job);
    }
    private SearchQuery query(String text) { return new SearchQuery(text, null, null, null, null, "", "", null, "relevance", 0); }
}
