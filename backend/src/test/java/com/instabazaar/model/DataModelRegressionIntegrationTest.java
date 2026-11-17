package com.instabazaar.model;

import com.instabazaar.account.model.Role;
import com.instabazaar.account.model.User;
import com.instabazaar.catalog.model.*;
import com.instabazaar.storefront.model.SellerProfile;
import com.instabazaar.wishlist.model.WishlistItem;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Database regressions only; this introduces no domain API or business service. */
@EnabledIfEnvironmentVariable(named = "INSTA_INTEGRATION_TEST", matches = "true")
@SpringBootTest
class DataModelRegressionIntegrationTest {
    @PersistenceContext EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

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

    private Product product() {
        String suffix = UUID.randomUUID().toString();
        var user = new User("Seller", "regression-" + suffix + "@example.com", "test-only-hash", Role.SELLER);
        var category = new Category("Regression " + suffix, "regression-" + suffix);
        var seller = new SellerProfile(user, "Regression store", "");
        em.persist(user); em.persist(category); em.persist(seller);
        var product = new Product(seller, category, "Dress", "", new BigDecimal("123.45"), "red");
        em.persist(product);
        return product;
    }

    @ParameterizedTest
    @ValueSource(strings = {" \tParty Wear\n ", "İPEK", "ΣΧΕΔΙΟ", "Été", "\u2003Cotton\u2003"})
    @Transactional
    void databaseOwnsNormalizedKeysForWhitespaceAndUnicode(String input) {
        var tag = new ProductTag(product(), input, TagSource.SELLER, TagStatus.APPROVED);
        em.persist(tag);
        em.flush();
        String expected = jdbc.queryForObject("SELECT lower(btrim(?::text))", String.class, input.strip());
        assertThat(tag.getTag()).isEqualTo(input.strip());
        assertThat(tag.getNormalizedTag()).isEqualTo(expected);
        tag.setTag("\nSummer Dress\t");
        em.flush();
        assertThat(tag.getNormalizedTag()).isEqualTo("summer dress");
        em.clear();
        assertThat(em.find(ProductTag.class, tag.getId()).getNormalizedTag()).isEqualTo("summer dress");
    }

    @Test
    @Transactional
    void repositorySavePersistsNewAssignedAndCompositeIdsAndLoadsAsExisting() {
        var category = new Category("Assigned", "assigned-" + UUID.randomUUID());
        var categories = new SimpleJpaRepository<Category, UUID>(Category.class, em);
        assertThat(category.isNew()).isTrue();
        assertThat(categories.save(category)).isSameAs(category);
        em.flush();
        assertThat(category.isNew()).isFalse();
        UUID categoryId = category.getId();
        em.clear();
        assertThat(categories.findById(categoryId).orElseThrow().isNew()).isFalse();

        var product = product();
        var size = new ProductSize(product, "M");
        var sizes = new SimpleJpaRepository<ProductSize, ProductSizeId>(ProductSize.class, em);
        assertThat(sizes.save(size)).isSameAs(size);
        var wishlist = new WishlistItem(product.getSeller().getUser(), product);
        var wishlists = new SimpleJpaRepository<WishlistItem, com.instabazaar.wishlist.model.WishlistItemId>(WishlistItem.class, em);
        assertThat(wishlists.save(wishlist)).isSameAs(wishlist);
        em.flush();
        assertThat(size.isNew()).isFalse();
        assertThat(wishlist.isNew()).isFalse();
        em.clear();
        assertThat(sizes.findById(size.getId()).orElseThrow().isNew()).isFalse();
        assertThat(wishlists.findById(wishlist.getId()).orElseThrow().isNew()).isFalse();
    }

    @Test
    void rejectsConcurrentStaleProductUpdate() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID[] ids = transaction.execute(status -> {
            var product = product();
            em.flush();
            return new UUID[] {product.getId(), product.getSeller().getId(),
                product.getSeller().getUser().getId(), product.getCategory().getId()};
        });
        try (var first = emf.createEntityManager(); var second = emf.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var firstCopy = first.find(Product.class, ids[0]);
            var staleCopy = second.find(Product.class, ids[0]);
            firstCopy.setTitle("First update");
            first.getTransaction().commit();
            staleCopy.setTitle("Stale update");
            assertThatThrownBy(() -> second.getTransaction().commit())
                .isInstanceOf(RollbackException.class)
                .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM product WHERE id = ?", ids[0]);
                jdbc.update("DELETE FROM seller_profile WHERE id = ?", ids[1]);
                jdbc.update("DELETE FROM app_user WHERE id = ?", ids[2]);
                jdbc.update("DELETE FROM category WHERE id = ?", ids[3]);
            });
        }
    }
}
