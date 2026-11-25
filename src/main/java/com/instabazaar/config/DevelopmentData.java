package com.instabazaar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.entity.*;
import com.instabazaar.enums.Role;
import com.instabazaar.repository.*;
import com.instabazaar.service.TagSuggestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile({"dev", "demo"})
public class DevelopmentData implements ApplicationRunner {
    private final UserRepository users;
    private final SellerRepository sellers;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final PasswordEncoder passwords;
    private final TagSuggestionService suggestions;
    private final ObjectMapper json;
    private final String demoPassword;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedDemo;

    public DevelopmentData(UserRepository users, SellerRepository sellers, ProductRepository products,
            CategoryRepository categories, PasswordEncoder passwords, TagSuggestionService suggestions, ObjectMapper json,
            @Value("${instabazaar.demo-password}") String demoPassword,
            @Value("${instabazaar.admin-email}") String adminEmail,
            @Value("${instabazaar.admin-password}") String adminPassword,
            @Value("${instabazaar.seed-demo}") boolean seedDemo) {
        this.users = users; this.sellers = sellers; this.products = products; this.categories = categories;
        this.passwords = passwords; this.suggestions = suggestions; this.json = json;
        this.demoPassword = demoPassword; this.adminEmail = adminEmail; this.adminPassword = adminPassword; this.seedDemo = seedDemo;
    }

    @Override @Transactional(rollbackFor = Exception.class) public void run(ApplicationArguments arguments) throws Exception {
        if (!adminEmail.isBlank() && !adminPassword.isBlank() && !users.existsByEmail(adminEmail.strip().toLowerCase(Locale.ROOT))) {
            validatePassword(adminPassword);
            if (!adminEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || adminEmail.length() > 190) throw new IllegalStateException("ADMIN_EMAIL is invalid.");
            createUser("Administrator", adminEmail.strip().toLowerCase(Locale.ROOT), Role.ADMIN, adminPassword, false);
        }
        if (!seedDemo) return;
        String password = demoPassword.isBlank() ? UUID.randomUUID().toString() : demoPassword;
        validatePassword(password);
        if (!users.existsByEmail("seller@example.test")) seedOriginalCatalogue(password);
        seedPhotoCatalogue(password);
    }

    private void seedOriginalCatalogue(String password) throws Exception {
        var first = createStore(createUser("Sample Seller", "seller@example.test", Role.SELLER, password, true), "Sample Loom Studio");
        var second = createStore(createUser("Sample Boutique", "edit@example.test", Role.SELLER, password, true), "The Everyday Edit");
        createUser("Sample Buyer", "buyer@example.test", Role.BUYER, password, true);
        try (var stream = new ClassPathResource("demo/catalogue.json").getInputStream()) {
            var catalogue = json.readTree(stream).get("products");
            int index = 0;
            List<Category> categoryList = categories.findAllByOrderByIdAsc();
            for (var item : catalogue) {
                Product product = new Product();
                product.seller = index++ % 2 == 0 ? first : second;
                int categoryIndex = Integer.parseInt(item.get("categoryId").asText().substring(35)) - 1;
                product.category = categoryList.get(categoryIndex); product.title = item.get("title").asText();
                product.description = item.get("description").asText(); product.price = new BigDecimal(item.get("price").asText());
                product.colour = item.get("colour").asText(); product.material = item.get("material").asText();
                product.occasion = item.get("occasion").asText(); product.brand = item.get("brand").asText();
                List<String> sizes = new ArrayList<>(); item.get("sizes").forEach(size -> sizes.add(size.asText()));
                product.sizes = String.join(", ", sizes); product.stock = item.get("stockQuantity").asInt();
                product.caption = item.get("reelCaption").asText(); product.illustration = item.get("illustration").asText();
                product.demo = true;
                Set<String> tags = new LinkedHashSet<>(); item.get("sellerTags").forEach(tag -> tags.add(tag.asText()));
                tags.addAll(suggestions.suggest(product.getTitle() + " " + product.getCaption()));
                for (String label : tags) { ProductTag tag = new ProductTag(); tag.product = product; tag.label = label; product.getTags().add(tag); }
                products.save(product);
            }
        }
    }

    private void seedPhotoCatalogue(String password) throws Exception {
        try (var stream = new ClassPathResource("demo/photo-catalogue.json").getInputStream()) {
            var catalogue = json.readTree(stream);
            Map<String, SellerProfile> stores = new LinkedHashMap<>();
            for (var item : catalogue.get("sellers")) {
                String email = item.get("email").asText();
                AppUser user = users.findByEmail(email).orElseGet(() ->
                        createUser(item.get("name").asText(), email, Role.SELLER, password, true));
                if (!user.getDemo() || user.getRole() != Role.SELLER) {
                    throw new IllegalStateException("Cannot attach demo listings to a non-demo seller: " + email);
                }
                stores.put(email, sellers.findByUserId(user.getId()).orElseGet(() ->
                        createStore(user, item.get("storeName").asText())));
            }
            for (var item : catalogue.get("products")) {
                SellerProfile store = stores.get(item.get("sellerEmail").asText());
                String photo = item.get("photo").asText();
                if (products.existsBySellerIdAndDemoTrueAndIllustration(store.getId(), photo)) continue;
                if (!new ClassPathResource("static/images/" + photo).exists()) {
                    throw new IllegalStateException("Missing demo product photograph: " + photo);
                }
                String categoryName = item.get("category").asText();
                Category category = categories.findByName(categoryName).orElseGet(() -> {
                    Category added = new Category(); added.name = categoryName; return categories.save(added);
                });
                Product product = new Product();
                product.seller = store; product.category = category; product.title = item.get("title").asText();
                product.description = item.get("description").asText()
                        + " Fictional demo listing with a representative stock photograph, not actual seller inventory."
                        + " Any visible brand marks do not imply affiliation or verified authenticity.";
                product.price = new BigDecimal(item.get("price").asText());
                product.colour = item.get("colour").asText(); product.material = item.get("material").asText();
                product.occasion = item.get("occasion").asText(); product.sizes = item.get("sizes").asText();
                product.stock = item.get("stock").asInt(); product.caption = item.get("caption").asText();
                product.illustration = photo; product.demo = true;
                Set<String> tags = new LinkedHashSet<>(); item.get("tags").forEach(tag -> tags.add(tag.asText()));
                tags.addAll(suggestions.suggest(product.getTitle() + " " + product.getCaption()));
                for (String label : tags) {
                    ProductTag tag = new ProductTag(); tag.product = product; tag.label = label; product.tags.add(tag);
                }
                products.save(product);
            }
        }
    }

    private AppUser createUser(String name, String email, Role role, String password, boolean demo) {
        AppUser user = new AppUser(); user.displayName = name; user.email = email; user.role = role;
        user.passwordHash = passwords.encode(password); user.demo = demo; return users.save(user);
    }
    private SellerProfile createStore(AppUser user, String name) {
        SellerProfile seller = new SellerProfile(); seller.user = user; seller.storeName = name;
        seller.description = "Fictional demonstration store featuring independent fashion. All listings are sample content, not offers for sale.";
        seller.publicEmail = "hello@example.test"; return sellers.save(seller);
    }
    private void validatePassword(String password) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Development account passwords must have at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }
}
