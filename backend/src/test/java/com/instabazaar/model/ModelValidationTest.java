package com.instabazaar.model;

import com.instabazaar.account.model.Role;
import com.instabazaar.account.model.User;
import com.instabazaar.catalog.model.Category;
import com.instabazaar.catalog.model.Product;
import com.instabazaar.moderation.model.Report;
import com.instabazaar.moderation.model.ReportReason;
import com.instabazaar.storefront.model.SellerProfile;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModelValidationTest {
    private SellerProfile seller() {
        return new SellerProfile(new User("Seller", "seller@example.com", "test-only-hash", Role.SELLER), "Store", "");
    }

    @Test
    void rejectsPriceThatPostgresqlWouldRoundOrOverflow() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var product = new Product(seller(), new Category("Test", "test"), "Dress", "", new BigDecimal("10.001"), "red");
            assertThat(validator.validate(product)).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
            product.setPrice(new BigDecimal("10000000000.00"));
            assertThat(validator.validate(product)).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
            product.setPrice(new BigDecimal("9999999999.99"));
            assertThat(validator.validate(product)).isEmpty();
        }
    }

    @Test
    void validatesCanonicalPhoneWithoutMakingOptionalContactsRequired() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var profile = seller();
            assertThat(validator.validate(profile)).isEmpty();
            profile.setPublicPhone("+919876543210");
            assertThat(validator.validate(profile)).isEmpty();
            profile.setPublicPhone("9876543210");
            assertThat(validator.validate(profile)).anyMatch(v -> v.getPropertyPath().toString().equals("publicPhone"));
        }
    }

    @Test
    void refusesAmbiguousReportTargets() {
        var user = new User("Buyer", "buyer@example.com", "test-only-hash", Role.BUYER);
        assertThatThrownBy(() -> new Report(user, null, null, ReportReason.SPAM))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
