package com.instabazaar.service;

import com.instabazaar.dto.*;
import com.instabazaar.entity.*;
import com.instabazaar.enums.Role;
import com.instabazaar.enums.ListingStatus;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.*;
import com.instabazaar.security.CurrentAccount;
import com.instabazaar.util.*;
import com.instabazaar.video.VideoJobs;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service @Validated @Transactional(readOnly = true)
public class CatalogService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SellerRepository sellers;
    private final CurrentAccount current;
    private final ViewMapper mapper;
    private final TagSuggestionService suggestions;
    private final MediaStorage storage;
    private final VideoJobs videos;

    public CatalogService(ProductRepository products, CategoryRepository categories, SellerRepository sellers,
                          CurrentAccount current, ViewMapper mapper, TagSuggestionService suggestions, MediaStorage storage, VideoJobs videos) {
        this.products = products; this.categories = categories; this.sellers = sellers; this.current = current;
        this.mapper = mapper; this.suggestions = suggestions; this.storage = storage; this.videos = videos;
    }

    public List<Views.CategoryView> categories() {
        return categories.findAllByOrderByIdAsc().stream().map(category -> new Views.CategoryView(category.getId(), category.getName())).toList();
    }

    public Views.ProductView product(Long id) {
        Product product = publicEntity(id);
        return mapper.product(product);
    }

    public Product publicEntity(Long id) {
        Product product = products.findById(id).orElseThrow(DomainException::missing);
        if (!mapper.visible(product)) throw DomainException.missing();
        return product;
    }

    public Views.SellerView seller(Long id) {
        SellerProfile seller = sellers.findById(id).filter(profile -> profile.getUser().getActive()).orElseThrow(DomainException::missing);
        return mapper.seller(seller);
    }

    public List<Views.SellerView> sellers(String query) {
        if (Text.clean(query).length() > 160) throw DomainException.invalid("Search must be at most 160 characters.");
        return sellers.findByUserActiveTrueAndStoreNameContainingIgnoreCase(Text.clean(query), PageRequest.of(0, 48))
                .stream().map(mapper::seller).toList();
    }

    @PreAuthorize("hasRole('SELLER')")
    public Views.SellerView ownStore() { return mapper.seller(ownSeller()); }

    @PreAuthorize("hasRole('SELLER')")
    public List<Views.ProductView> ownProducts() {
        return products.findBySellerUserIdOrderByCreatedAtDesc(current.require().getId()).stream().map(mapper::product).toList();
    }

    @PreAuthorize("hasRole('SELLER')")
    public Views.ProductView ownProduct(Long id) { return mapper.product(owned(id)); }

    @PreAuthorize("hasRole('SELLER')")
    public Forms.ProductInput editor(Long id) {
        Product product = owned(id);
        return new Forms.ProductInput(product.getTitle(), product.getDescription(), product.getPrice(), product.getCategory().getId(),
                product.getColour(), product.getSizes(), product.getMaterial(), product.getOccasion(), product.getBrand(), product.getStock(),
                product.getStatus(), product.getSourceUrl(), product.getCaption(),
                String.join(", ", product.getTags().stream().filter(tag -> !tag.getSuggested()).map(tag -> tag.getLabel()).sorted().toList()), product.getVersion());
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public Long save(Long id, @Valid Forms.ProductInput form, MultipartFile image) {
        SellerProfile seller = ownSeller();
        Product product = id == null ? new Product() : ownedForUpdate(id);
        if (id != null && product.getVersion() != form.version()) throw DomainException.invalid("This product changed in another session. Reload before editing.");
        String source = ExternalLinks.validate(form.sourceUrl(), "social media");
        Category category = categories.findById(form.categoryId()).orElseThrow(() -> DomainException.invalid("Choose an existing category."));
        Set<String> manual = new LinkedHashSet<>();
        for (String raw : Text.clean(form.tags()).split(",")) {
            String label = Text.normalized(raw);
            if (label.length() > 50) throw DomainException.invalid("Each tag must be at most 50 characters.");
            if (!label.isEmpty()) manual.add(label);
        }
        if (manual.size() > 24) throw DomainException.invalid("Use at most 24 seller tags.");
        product.seller = seller; product.category = category; product.title = Text.clean(form.title());
        product.description = Text.clean(form.description()); product.price = form.price();
        product.colour = Text.clean(form.colour()); product.sizes = Text.clean(form.sizes());
        product.material = Text.clean(form.material()); product.occasion = Text.clean(form.occasion());
        product.brand = Text.clean(form.brand()); product.stock = form.stock(); product.status = form.status();
        product.sourceUrl = source; product.caption = Text.clean(form.caption());
        Set<String> generated = new LinkedHashSet<>(suggestions.suggest(String.join(" ", product.getTitle(), product.getDescription(),
                product.getColour(), product.getMaterial(), product.getOccasion(), product.getCategory().getName(), product.getCaption())));
        Set<String> all = new LinkedHashSet<>(manual); all.addAll(generated);
        product.getTags().removeIf(tag -> !all.contains(tag.getLabel()));
        for (String label : all) {
            ProductTag tag = product.getTags().stream().filter(existing -> existing.getLabel().equals(label)).findFirst().orElse(null);
            if (tag == null) { tag = new ProductTag(); tag.product = product; tag.label = label; product.getTags().add(tag); }
            tag.suggested = !manual.contains(label);
        }
        if (image != null && !image.isEmpty()) {
            if (product.getImages().size() >= 5) throw DomainException.invalid("A listing can have up to five images. Remove one first.");
            var stored = storage.store(image);
            cleanup(stored.key(), false);
            ProductImage saved = new ProductImage(); saved.product = product;
            saved.storageKey = stored.key(); saved.contentType = stored.contentType(); product.getImages().add(saved);
        }
        return products.saveAndFlush(product).getId();
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public void archive(Long id) { ownedForUpdate(id).status = ListingStatus.ARCHIVED; }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public Long save(Long id, @Valid Forms.ProductInput form, MultipartFile image, Long videoJobId) {
        Long savedId = save(id, form, image);
        if (videoJobId != null) videos.attach(videoJobId, id, products.findById(savedId).orElseThrow(DomainException::missing));
        return savedId;
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public void removeImage(Long productId, String key) {
        Product product = ownedForUpdate(productId);
        ProductImage image = product.getImages().stream().filter(existing -> existing.getStorageKey().equals(key)).findFirst().orElseThrow(DomainException::missing);
        product.getImages().remove(image);
        cleanup(key, true);
    }

    @Transactional @PreAuthorize("hasRole('SELLER')")
    public void updateStore(@Valid Forms.Store form, MultipartFile logo) {
        SellerProfile seller = ownSeller();
        String whatsapp = ExternalLinks.validate(form.whatsappUrl(), "whatsapp");
        String instagram = ExternalLinks.validate(form.instagramUrl(), "instagram");
        String facebook = ExternalLinks.validate(form.facebookUrl(), "facebook");
        seller.storeName = Text.clean(form.storeName()); seller.description = Text.clean(form.description());
        seller.publicEmail = Text.clean(form.publicEmail()); seller.whatsappUrl = whatsapp;
        seller.instagramUrl = instagram; seller.facebookUrl = facebook;
        if (logo != null && !logo.isEmpty()) {
            var stored = storage.store(logo); cleanup(stored.key(), false);
            if (seller.getLogoKey() != null) cleanup(seller.getLogoKey(), true);
            seller.logoKey = stored.key();
        }
        sellers.saveAndFlush(seller);
    }

    private Product owned(Long id) {
        Product product = products.findById(id).orElseThrow(DomainException::missing);
        if (!product.getSeller().getUser().getId().equals(current.require().getId())) throw DomainException.forbidden();
        return product;
    }

    private SellerProfile ownSeller() {
        return sellers.findByUserId(current.require().getId()).orElseThrow(DomainException::forbidden);
    }

    private Product ownedForUpdate(Long id) {
        Product product = products.lockById(id).orElseThrow(DomainException::missing);
        if (!product.getSeller().getUser().getId().equals(current.require().getId())) throw DomainException.forbidden();
        return product;
    }

    private void cleanup(String key, boolean afterCommit) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if ((afterCommit && status == STATUS_COMMITTED) || (!afterCommit && status != STATUS_COMMITTED)) {
                    try { storage.delete(key); }
                    catch (RuntimeException exception) { org.slf4j.LoggerFactory.getLogger(CatalogService.class).warn("Stored image cleanup requires retry."); }
                }
            }
        });
    }
}
