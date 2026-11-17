package com.instabazaar.model;

import com.instabazaar.account.model.*;
import com.instabazaar.catalog.model.*;
import com.instabazaar.media.model.*;
import com.instabazaar.moderation.model.*;
import com.instabazaar.review.model.*;
import com.instabazaar.storefront.model.SellerProfile;
import com.instabazaar.wishlist.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Opt in only against an expendable dedicated PostgreSQL test database. */
@EnabledIfEnvironmentVariable(named = "INSTA_INTEGRATION_TEST", matches = "true")
@SpringBootTest
@Transactional
class DataModelIntegrationTest {
    @PersistenceContext EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> required("TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    @Test
    void roundTripsAllMappingsCompositeKeysJsonAndOptimisticVersion() {
        String suffix = UUID.randomUUID().toString();
        var seller = new User("Seller", "seller-" + suffix + "@example.com", "test-only-hash", Role.SELLER);
        var buyer = new User("Buyer", "buyer-" + suffix + "@example.com", "test-only-hash", Role.BUYER);
        var admin = new User("Admin", "admin-" + suffix + "@example.com", "test-only-hash", Role.ADMIN);
        em.persist(seller); em.persist(buyer); em.persist(admin);
        var category = new Category("Test " + suffix, "test-" + suffix);
        em.persist(category);
        var asset = new MediaAsset(seller, "test/" + suffix, "image/png", 1024L, 32, 32);
        em.persist(asset);
        var store = new SellerProfile(seller, "Test store", "Description");
        store.setLogoAsset(asset);
        em.persist(store);
        var product = new Product(store, category, "Dress", "Description", new BigDecimal("123.45"), "red");
        em.persist(product);
        var size = new ProductSize(product, "M");
        var wishlist = new WishlistItem(buyer, product);
        em.persist(size); em.persist(wishlist);
        var image = new ProductImage(product, asset, (short) 0, "Front view");
        var tag = new ProductTag(product, "Party Wear", TagSource.SELLER, TagStatus.APPROVED);
        var review = new Review(buyer, product, (short) 5);
        em.persist(image); em.persist(tag); em.persist(review);
        var report = new Report(buyer, null, review, ReportReason.SPAM);
        em.persist(report);
        var audit = new AdminAuditEvent(admin, AuditAction.REVIEW_MODERATION, AuditTargetType.REVIEW,
            review.getId(), "Reviewed", "{\"status\":\"PENDING\"}", "{\"status\":\"APPROVED\"}");
        em.persist(audit);
        em.flush();
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isEqualTo(product.getCreatedAt());
        assertThat(product.getVersion()).isZero();
        product.setTitle("Updated dress");
        em.flush();
        assertThat(product.getVersion()).isEqualTo(1L);
        em.clear();
        assertThat(em.find(Product.class, product.getId()).getPrice()).isEqualByComparingTo("123.45");
        assertThat(em.find(ProductSize.class, size.getId()).getSizeCode()).isEqualTo("M");
        assertThat(em.find(WishlistItem.class, wishlist.getId()).getProduct().getId()).isEqualTo(product.getId());
        assertThat(em.find(ProductTag.class, tag.getId()).getNormalizedTag()).isEqualTo("party wear");
        assertThat(em.find(AdminAuditEvent.class, audit.getId()).getAfterState()).contains("APPROVED");
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(after_state) FROM admin_audit_event WHERE id = ?", String.class, audit.getId())).isEqualTo("object");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3','4')", Integer.class)).isEqualTo(4);
    }
}
