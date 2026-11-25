package com.instabazaar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.config.DevelopmentData;
import com.instabazaar.dto.SearchQuery;
import com.instabazaar.dto.Views;
import com.instabazaar.entity.Product;
import com.instabazaar.enums.ListingStatus;
import com.instabazaar.enums.Role;
import com.instabazaar.service.TagSuggestionService;
import com.instabazaar.service.ViewMapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevelopmentDataTest extends ApplicationTestSupport {
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired TagSuggestionService suggestions;
    @Autowired ViewMapper views;

    @Test void freshSeedAddsTenUniquePhotosAcrossThreeSellersAndDoesNotDuplicateOnRestart() throws Exception {
        long originalCount = products.count();
        seed(true);
        var photographed = photoProducts();
        assertThat(products.count()).isEqualTo(originalCount + 16);
        assertThat(photographed).hasSize(10);
        assertThat(photographed).extracting(product -> product.getSeller().getStoreName())
                .containsOnly("Sample Loom Studio", "The Everyday Edit", "Urban Trail Outfitters");
        assertThat(photographed.stream().filter(product -> product.getSeller().getStoreName().equals("Sample Loom Studio"))).hasSize(3);
        assertThat(photographed.stream().filter(product -> product.getSeller().getStoreName().equals("The Everyday Edit"))).hasSize(3);
        assertThat(photographed.stream().filter(product -> product.getSeller().getStoreName().equals("Urban Trail Outfitters"))).hasSize(4);
        var edited = photographed.getFirst();
        edited.title = "Seller edited title"; edited.stock = 0;
        edited.status = ListingStatus.ARCHIVED; edited.hidden = true;
        String hash = users.findByEmail("seller@example.test").orElseThrow().getPasswordHash();
        long userCount = users.count(); long categoryCount = categories.count();
        seed(true);
        assertThat(products.count()).isEqualTo(originalCount + 16);
        assertThat(photoProducts()).hasSize(10);
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(categories.count()).isEqualTo(categoryCount);
        assertThat(edited.getTitle()).isEqualTo("Seller edited title");
        assertThat(edited.getStock()).isZero(); assertThat(edited.getStatus()).isEqualTo(ListingStatus.ARCHIVED);
        assertThat(edited.getHidden()).isTrue();
        assertThat(users.findByEmail("seller@example.test").orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(products.findById(productId).orElseThrow().getTitle()).isEqualTo("Red embroidered party dress");
    }

    @Test void existingDemoDatabaseReceivesOnlyTenAdditionsWithSearchablePhotos() throws Exception {
        var existing = account("Existing demo seller", "seller@example.test", Role.SELLER);
        existing.demo = true; store(existing, "My existing demo store");
        String hash = existing.getPasswordHash();
        long count = products.count();
        seed(true);
        assertThat(products.count()).isEqualTo(count + 10);
        assertThat(existing.getPasswordHash()).isEqualTo(hash);
        assertThat(sellers.findByUserId(existing.getId()).orElseThrow().getStoreName()).isEqualTo("My existing demo store");
        assertThat(search.search(new SearchQuery("burgundy canvas sneakers", null, null, null, null, "", "", null, "relevance", 0)).products())
                .extracting(Views.ProductView::title).contains("Burgundy Canvas Low-Top Sneakers");
        var hashes = new HashSet<String>();
        for (Product product : photoProducts()) {
            assertThat(product.getDemo()).isTrue(); assertThat(product.getTags()).isNotEmpty();
            String url = views.product(product).imageUrl();
            assertThat(url).startsWith("/images/demo/").endsWith(".jpg");
            byte[] bytes = new ClassPathResource("static" + url).getContentAsByteArray();
            assertThat(ImageIO.read(new ByteArrayInputStream(bytes))).isNotNull();
            hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
            String page = mvc.perform(get("/products/" + product.getId())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains(url, "Sample imagery, not actual seller inventory.")
                    .doesNotContain("Illustration, not a product photograph.");
        }
        assertThat(hashes).hasSize(10);
    }

    @Test void seedDisabledDoesNotCreateDemoProductsOrAccounts() throws Exception {
        long count = products.count(); long userCount = users.count();
        seed(false);
        assertThat(products.count()).isEqualTo(count); assertThat(users.count()).isEqualTo(userCount);
    }

    @Test void reservedEmailCannotAttachDemoListingsToARealSeller() {
        var real = account("Real seller", "seller@example.test", Role.SELLER);
        store(real, "Real store");
        long count = products.count();
        assertThatThrownBy(() -> seed(true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-demo seller");
        assertThat(products.count()).isEqualTo(count); assertThat(real.getDemo()).isFalse();
    }

    private List<Product> photoProducts() {
        return products.findAll().stream().filter(product -> product.getDemo()
                && product.getIllustration() != null && product.getIllustration().startsWith("demo/")).toList();
    }

    private void seed(boolean enabled) throws Exception {
        new DevelopmentData(users, sellers, products, categories, passwords, suggestions, json, "", "", "", enabled)
                .run(new DefaultApplicationArguments());
    }
}
