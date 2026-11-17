package com.instabazaar.core;

import static org.assertj.core.api.Assertions.*;

import com.instabazaar.account.model.Role;
import com.instabazaar.account.service.*;
import com.instabazaar.catalog.dto.ProductWrite;
import com.instabazaar.catalog.model.*;
import com.instabazaar.catalog.service.*;
import com.instabazaar.media.service.MediaService;
import com.instabazaar.media.storage.MediaStorage;
import com.instabazaar.moderation.model.*;
import com.instabazaar.moderation.service.*;
import com.instabazaar.review.model.ReviewStatus;
import com.instabazaar.review.service.ReviewService;
import com.instabazaar.search.dto.SearchCriteria;
import com.instabazaar.search.service.SearchService;
import com.instabazaar.security.*;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.storefront.dto.StoreWrite;
import com.instabazaar.storefront.service.StorefrontService;
import com.instabazaar.wishlist.service.WishlistService;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;

@EnabledIfEnvironmentVariable(named = "INSTA_INTEGRATION_TEST", matches = "true")
@SpringBootTest
class CoreLogicIntegrationTest {
  static Path mediaRoot = Path.of("target", "core-media-" + UUID.randomUUID()).toAbsolutePath();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", () -> required("TEST_DB_URL"));
    p.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
    p.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
    p.add("instabazaar.media.directory", () -> mediaRoot.toString());
  }

  static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException(name + " is required for disposable integration tests.");
    return value;
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ProductService products;
  @Autowired TagService tags;
  @Autowired CategoryService categories;
  @Autowired CatalogReadService catalog;
  @Autowired StorefrontService stores;
  @Autowired SearchService search;
  @Autowired WishlistService wishlist;
  @Autowired ReviewService reviews;
  @Autowired ReportService reports;
  @Autowired ModerationService moderation;
  @Autowired AdminQueryService admin;
  @Autowired AccountService accounts;
  @Autowired AuthenticationService authentication;
  @Autowired MediaService media;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean MediaStorage storage;
  @Autowired PlatformTransactionManager manager;
  static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-000000000001");
  UUID seller, buyer, administrator, store;

  @BeforeEach
  void fixture() {
    // This class is opt-in and points only at TEST_DB_URL. No application DB is used.
    seller = user("SELLER");
    buyer = user("BUYER");
    administrator = user("ADMIN");
    as(seller);
    store =
        stores
            .replace(
                new StoreWrite(
                    "Store " + seller,
                    "Store description",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
            .id();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  UUID user(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into app_user(id,name,email,password_hash,role) values(?,?,?,?,?)",
        id,
        "User " + id,
        id + "@example.test",
        "fixture-only-hash",
        role);
    return id;
  }

  static void as(UUID id) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new SessionPrincipal(id), null, List.of()));
  }

  ProductWrite request(String title) {
    return new ProductWrite(
        CATEGORY,
        title,
        "An embroidered dress for party wear",
        new BigDecimal("1499.00"),
        "INR",
        " Red ",
        List.of(" s ", "M"),
        "cotton",
        "party wear",
        null,
        12,
        true,
        null,
        List.of("seller exclusive"),
        List.of());
  }

  Contracts.ManagedProduct draft() {
    as(seller);
    return products.create(request("Red embroidered dress " + UUID.randomUUID()));
  }

  Contracts.ManagedProduct active() {
    var p = draft();
    return products.status(p.product().id(), ListingStatus.ACTIVE, p.product().version());
  }

  void code(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  SearchCriteria query(String q) {
    return new SearchCriteria(q, null, store, null, null, null, null, null, null, 0, 20);
  }

  @Test
  void trustedIdentityRejectsAnonymousFakePrincipalAndDisabledSessions() {
    SecurityContextHolder.clearContext();
    code(accounts::me, "AUTHENTICATION_REQUIRED");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(seller.toString(), null, List.of()));
    code(accounts::me, "AUTHENTICATION_REQUIRED");
    as(seller);
    jdbc.update("update app_user set enabled=false where id=?", seller);
    code(accounts::me, "ACCOUNT_DISABLED");
    as(buyer);
    code(stores::own, "ACCESS_DENIED");
  }

  @Test
  void registrationNormalizesAndDoesNotLogIn() {
    SecurityContextHolder.clearContext();
    String email = "NEW-" + UUID.randomUUID() + "@EXAMPLE.TEST";
    var user = authentication.register(" New User ", email, "a long test password", Role.BUYER);
    assertThat(user.email()).isEqualTo(email.toLowerCase(Locale.ROOT));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    code(
        () -> authentication.register("User", email, "a long test password", Role.BUYER),
        "EMAIL_EXISTS");
    code(
        () ->
            authentication.register(
                "Admin", "other@example.test", "a long test password", Role.ADMIN),
        "VALIDATION_FAILED");
    var auth = authentication.verifyCredentials(email, "a long test password");
    assertThat(auth.getPrincipal()).isEqualTo(new SessionPrincipal(user.id()));
    code(
        () -> authentication.verifyCredentials(email, "incorrect password"), "INVALID_CREDENTIALS");
    SecurityContextHolder.getContext().setAuthentication(auth);
    var changed = accounts.updateName("Changed", user.version());
    assertThat(changed.email()).isEqualTo(user.email());
    assertThat(changed.version()).isGreaterThan(user.version());
    code(() -> accounts.updateName("Stale", user.version()), "VERSION_CONFLICT");
  }

  @Test
  void productReplacementIsAtomicAndChildChangesInvalidateStaleEditors() {
    var initial = draft();
    UUID id = initial.product().id();
    assertThat(initial.listingStatus()).isEqualTo("DRAFT");
    assertThat(initial.product().sizes()).containsExactly("M", "S");
    code(() -> catalog.publicProduct(id), "RESOURCE_NOT_FOUND");
    var changed =
        products.replace(id, request(initial.product().title()), initial.product().version());
    assertThat(changed.product().version()).isGreaterThan(initial.product().version());
    code(
        () -> products.replace(id, request("Stale"), initial.product().version()),
        "VERSION_CONFLICT");
    var bad = request("Should roll back");
    var duplicate =
        new ProductWrite(
            bad.categoryId(),
            bad.title(),
            bad.description(),
            bad.price(),
            bad.currency(),
            bad.colour(),
            List.of("m", " M "),
            bad.material(),
            bad.occasion(),
            bad.brand(),
            bad.stockQuantity(),
            bad.availabilityEnabled(),
            bad.sourceUrl(),
            bad.sellerTags(),
            bad.images());
    code(() -> products.replace(id, duplicate, changed.product().version()), "VALIDATION_FAILED");
    assertThat(products.own(id).product().title()).isEqualTo(initial.product().title());
    UUID other = user("SELLER");
    as(other);
    code(() -> products.own(id), "RESOURCE_NOT_FOUND");
  }

  @Test
  void tagsPreserveProvenanceAndRejectedDecisions() {
    var p = draft();
    UUID id = p.product().id();
    var suggested = tags.suggest(id, p.product().version());
    var tag =
        suggested.items().stream()
            .filter(t -> t.source().equals("SYSTEM"))
            .findFirst()
            .orElseThrow();
    var rejected = tags.decide(id, tag.id(), TagStatus.REJECTED, suggested.productVersion());
    var regenerated = tags.suggest(id, rejected.productVersion());
    assertThat(
            regenerated.items().stream()
                .filter(t -> t.id().equals(tag.id()))
                .findFirst()
                .orElseThrow()
                .status())
        .isEqualTo("REJECTED");
    var r = request("Changed");
    var collision =
        new ProductWrite(
            r.categoryId(),
            r.title(),
            r.description(),
            r.price(),
            r.currency(),
            r.colour(),
            r.sizes(),
            r.material(),
            r.occasion(),
            r.brand(),
            r.stockQuantity(),
            r.availabilityEnabled(),
            r.sourceUrl(),
            List.of(tag.tag()),
            r.images());
    code(() -> products.replace(id, collision, regenerated.productVersion()), "VALIDATION_FAILED");
    code(
        () -> tags.decide(id, tag.id(), TagStatus.APPROVED, suggested.productVersion()),
        "VERSION_CONFLICT");
  }

  @Test
  void publishingAndArchiveReopeningFollowDeclaredPolicy() {
    var p = active();
    UUID id = p.product().id();
    p = products.status(id, ListingStatus.ARCHIVED, p.product().version());
    long archived = p.product().version();
    code(() -> products.status(id, ListingStatus.ACTIVE, archived), "INVALID_STATE_TRANSITION");
    code(() -> products.replace(id, request("No"), archived), "INVALID_STATE_TRANSITION");
    var reopened = products.status(id, ListingStatus.DRAFT, archived);
    assertThat(reopened.listingStatus()).isEqualTo("DRAFT");
    var storeDto = stores.own();
    stores.replace(
        new StoreWrite(
            storeDto.storeName(), "", null, null, null, null, null, null, storeDto.version()));
    code(
        () -> products.status(id, ListingStatus.ACTIVE, reopened.product().version()),
        "VALIDATION_FAILED");
  }

  @Test
  void searchUsesSqlFiltersRanksAndRefreshesStoreContent() {
    var p = active();
    UUID id = p.product().id();
    assertThat(search.search(query(p.product().title())).items())
        .extracting(Contracts.ProductSummary::id)
        .containsExactly(id);
    var filtered =
        new SearchCriteria(
            "embroidered",
            CATEGORY,
            store,
            new BigDecimal("1400.00"),
            new BigDecimal("1500.00"),
            " RED ",
            " m ",
            true,
            "price_asc",
            0,
            20);
    assertThat(search.search(filtered).items())
        .extracting(Contracts.ProductSummary::id)
        .containsExactly(id);
    var s = stores.own();
    String token = "uniquestore" + UUID.randomUUID().toString().replace("-", "");
    stores.replace(
        new StoreWrite(token, s.description(), null, null, null, null, null, null, s.version()));
    assertThat(search.search(query(token)).items())
        .extracting(Contracts.ProductSummary::id)
        .containsExactly(id);
    as(administrator);
    moderation.product(id, ModerationStatus.HIDDEN, "Moderated", p.product().version());
    assertThat(search.search(query(null)).items()).isEmpty();
    code(() -> catalog.publicProduct(id), "RESOURCE_NOT_FOUND");
  }

  @Test
  void wishlistRetainsUnavailableEntriesAndOutOfStockIsVisible() {
    var p = active();
    UUID id = p.product().id();
    as(buyer);
    wishlist.save(id);
    wishlist.save(id);
    assertThat(wishlist.list(0, 20).items()).hasSize(1);
    as(seller);
    var r = request(p.product().title());
    var noStock =
        new ProductWrite(
            r.categoryId(),
            r.title(),
            r.description(),
            r.price(),
            r.currency(),
            r.colour(),
            r.sizes(),
            r.material(),
            r.occasion(),
            r.brand(),
            0,
            true,
            null,
            r.sellerTags(),
            r.images());
    var changed = products.replace(id, noStock, p.product().version());
    as(buyer);
    assertThat(wishlist.list(0, 20).items().getFirst().visibility()).isEqualTo("VISIBLE");
    assertThat(wishlist.list(0, 20).items().getFirst().product().available()).isFalse();
    as(administrator);
    moderation.product(id, ModerationStatus.HIDDEN, "Hidden", changed.product().version());
    as(buyer);
    var saved = wishlist.list(0, 20).items().getFirst();
    assertThat(saved.visibility()).isEqualTo("UNAVAILABLE");
    assertThat(saved.product()).isNull();
    wishlist.remove(id);
    wishlist.remove(id);
    assertThat(wishlist.list(0, 20).items()).isEmpty();
  }

  @Test
  void reviewModerationControlsRatingsAndEditingReturnsToPending() {
    var p = active();
    UUID id = p.product().id();
    code(() -> reviews.create(id, 5, null), "SELF_REVIEW");
    as(buyer);
    var r = reviews.create(id, 4, null);
    assertThat(r.status()).isEqualTo("PENDING");
    code(() -> reviews.create(id, 5, null), "REVIEW_EXISTS");
    assertThat(catalog.publicProduct(id).rating().count()).isZero();
    as(administrator);
    var approved = moderation.review(r.id(), ReviewStatus.APPROVED, "Approved", r.version());
    assertThat(catalog.publicProduct(id).rating().average()).isEqualByComparingTo("4.00");
    as(buyer);
    var edited = reviews.replace(r.id(), 5, "Updated", approved.version());
    assertThat(edited.status()).isEqualTo("PENDING");
    assertThat(catalog.publicProduct(id).rating().average()).isNull();
    assertThat(reviews.publicReviews(id, 0, 20).items()).isEmpty();
  }

  @Test
  void reportClosureAuditsWithoutImplicitTargetModeration() {
    var p = active();
    as(buyer);
    var report =
        reports.create(ReportService.TargetType.PRODUCT, p.product().id(), ReportReason.SPAM, null);
    code(
        () ->
            reports.create(
                ReportService.TargetType.PRODUCT, p.product().id(), ReportReason.SPAM, null),
        "REPORT_EXISTS");
    as(administrator);
    var closed =
        moderation.closeReport(report.id(), ReportStatus.RESOLVED, "Reviewed", report.version());
    assertThat(closed.resolvedAt()).isNotNull();
    assertThat(catalog.publicProduct(p.product().id())).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_event where target_id=?",
                Integer.class,
                report.id()))
        .isEqualTo(1);
    assertThat(
            admin.reports(ReportService.TargetType.PRODUCT, ReportStatus.RESOLVED, 0, 100).items())
        .extracting(Contracts.Report::id)
        .contains(report.id());
    code(
        () ->
            moderation.closeReport(report.id(), ReportStatus.DISMISSED, "Again", closed.version()),
        "INVALID_STATE_TRANSITION");
  }

  @Test
  void auditFailureRollsBackTargetChange() {
    var p = active();
    as(administrator);
    jdbc.execute(
        "create or replace function step2_reject_audit() returns trigger language plpgsql as $$"
            + " begin if NEW.target_id = '"
            + p.product().id()
            + "'::uuid then raise exception 'test audit rejection'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger step2_audit_failure before insert on admin_audit_event for each row execute"
            + " function step2_reject_audit()");
    try {
      assertThatThrownBy(
              () ->
                  moderation.product(
                      p.product().id(),
                      ModerationStatus.HIDDEN,
                      "Should roll back",
                      p.product().version()))
          .isInstanceOf(RuntimeException.class);
      assertThat(catalog.publicProduct(p.product().id()).version())
          .isEqualTo(p.product().version());
    } finally {
      jdbc.execute("drop trigger step2_audit_failure on admin_audit_event");
      jdbc.execute("drop function step2_reject_audit()");
    }
  }

  @Test
  void mediaAttachmentChecksOwnershipReferencesAndContiguousOrdering() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
    as(seller);
    var asset = media.upload(new ByteArrayInputStream(bytes.toByteArray()));
    assertThat(asset.previewUrl()).doesNotContain(".bin");
    as(buyer);
    code(() -> media.authorizeRead(asset.id()), "RESOURCE_NOT_FOUND");
    as(seller);
    var r = request("With image");
    var withImage =
        new ProductWrite(
            r.categoryId(),
            r.title(),
            r.description(),
            r.price(),
            r.currency(),
            r.colour(),
            r.sizes(),
            r.material(),
            r.occasion(),
            r.brand(),
            1,
            true,
            null,
            r.sellerTags(),
            List.of(new ProductWrite.ImageWrite(asset.id(), 0, "Front")));
    var product = products.create(withImage);
    code(() -> media.delete(asset.id()), "ASSET_IN_USE");
    code(() -> products.create(withImage), "ASSET_IN_USE");
    product =
        products.replace(
            product.product().id(), request("Without image"), product.product().version());
    media.delete(asset.id());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_asset where id=?", Integer.class, asset.id()))
        .isZero();
  }

  @Test
  void concurrentProductEditsAndWishlistSavesHaveCorrectOutcomes() throws Exception {
    var p = active();
    UUID id = p.product().id();
    var edits =
        parallel(
            () -> {
              as(seller);
              try {
                products.replace(id, request("Concurrent A"), p.product().version());
                return "OK";
              } catch (BusinessException e) {
                return e.getCode();
              }
            },
            () -> {
              as(seller);
              try {
                products.replace(id, request("Concurrent B"), p.product().version());
                return "OK";
              } catch (BusinessException e) {
                return e.getCode();
              }
            });
    assertThat(edits).containsExactlyInAnyOrder("OK", "VERSION_CONFLICT");
    assertThat(
            parallel(
                () -> {
                  as(buyer);
                  wishlist.save(id);
                  return "OK";
                },
                () -> {
                  as(buyer);
                  wishlist.save(id);
                  return "OK";
                }))
        .containsOnly("OK");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from wishlist_item where user_id=? and product_id=?",
                Integer.class,
                buyer,
                id))
        .isEqualTo(1);
  }

  @Test
  void concurrentDuplicateReviewsAndReportsMapStableConflicts() throws Exception {
    var p = active();
    UUID id = p.product().id();
    Supplier<String> review =
        () -> {
          as(buyer);
          try {
            reviews.create(id, 4, "Concurrent");
            return "OK";
          } catch (BusinessException e) {
            return e.getCode();
          }
        };
    assertThat(parallel(review, review)).containsExactlyInAnyOrder("OK", "REVIEW_EXISTS");
    Supplier<String> report =
        () -> {
          as(buyer);
          try {
            reports.create(ReportService.TargetType.PRODUCT, id, ReportReason.SPAM, null);
            return "OK";
          } catch (BusinessException e) {
            return e.getCode();
          }
        };
    assertThat(parallel(report, report)).containsExactlyInAnyOrder("OK", "REPORT_EXISTS");
  }

  @Test
  void concurrentAdministratorsCannotDisableEachOther() throws Exception {
    UUID other = user("ADMIN");
    Supplier<String> first =
        () -> {
          as(administrator);
          try {
            moderation.account(other, false, "Disable", 0L);
            return "OK";
          } catch (BusinessException e) {
            return e.getCode();
          }
        };
    Supplier<String> second =
        () -> {
          as(other);
          try {
            moderation.account(administrator, false, "Disable", 0L);
            return "OK";
          } catch (BusinessException e) {
            return e.getCode();
          }
        };
    assertThat(parallel(first, second)).containsExactlyInAnyOrder("OK", "ACCOUNT_DISABLED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app_user where id in (?,?) and enabled",
                Integer.class,
                administrator,
                other))
        .isEqualTo(1);
    UUID remaining =
        jdbc.queryForObject(
            "select id from app_user where id in (?,?) and enabled",
            UUID.class,
            administrator,
            other);
    as(remaining);
    code(
        () -> moderation.account(remaining, false, "Self disable", 0L), "INVALID_STATE_TRANSITION");
  }

  @Test
  void failedMediaMetadataCompensatesBytesAndCleanupRetriesDeletion() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
    as(seller);
    long before;
    try (var files = java.nio.file.Files.list(mediaRoot)) {
      before = files.count();
    }
    jdbc.execute(
        "create or replace function step2_reject_media() returns trigger language plpgsql as $$"
            + " begin if NEW.owner_user_id = '"
            + seller
            + "'::uuid then raise exception 'test metadata rejection'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger step2_media_failure before insert on media_asset for each row execute"
            + " function step2_reject_media()");
    try {
      assertThatThrownBy(() -> media.upload(new ByteArrayInputStream(bytes.toByteArray())))
          .isInstanceOf(RuntimeException.class);
      try (var files = java.nio.file.Files.list(mediaRoot)) {
        assertThat(files.count()).isEqualTo(before);
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from media_asset where owner_user_id=?", Integer.class, seller))
          .isZero();
    } finally {
      jdbc.execute("drop trigger step2_media_failure on media_asset");
      jdbc.execute("drop function step2_reject_media()");
    }
    var asset = media.upload(new ByteArrayInputStream(bytes.toByteArray()));
    String key =
        jdbc.queryForObject(
            "select storage_key from media_asset where id=?", String.class, asset.id());
    jdbc.update(
        "update media_asset set created_at=current_timestamp-interval '2 days' where id=?",
        asset.id());
    org.mockito.Mockito.doThrow(new IOException("test transient failure"))
        .doCallRealMethod()
        .when(storage)
        .delete(key);
    media.cleanup();
    assertThat(
            jdbc.queryForObject(
                "select state from media_asset where id=?", String.class, asset.id()))
        .isEqualTo("DELETING");
    media.cleanup();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_asset where id=?", Integer.class, asset.id()))
        .isZero();
    assertThat(java.nio.file.Files.exists(mediaRoot.resolve(key))).isFalse();
  }

  @Test
  void categoryRenameRefreshesSearchAndPaginationDoesNotMultiplyRows() {
    var first = active();
    var second = active();
    var criteria =
        new SearchCriteria(null, CATEGORY, store, null, null, null, null, null, "newest", 0, 1);
    var page = search.search(criteria);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.items()).hasSize(1);
    String previous =
        jdbc.queryForObject("select name from category where id=?", String.class, CATEGORY);
    String token = "category" + UUID.randomUUID().toString().replace("-", "");
    as(administrator);
    try {
      categories.rename(CATEGORY, token);
      assertThat(search.search(query(token)).items())
          .extracting(Contracts.ProductSummary::id)
          .containsExactlyInAnyOrder(first.product().id(), second.product().id());
    } finally {
      categories.rename(CATEGORY, previous);
    }
    assertThat(admin.statistics().publicProducts()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void privateReadHelpersAlsoEnforceOwnership() {
    var p = draft();
    as(buyer);
    code(() -> catalog.managed(p.product().id()), "RESOURCE_NOT_FOUND");
    code(() -> catalog.store(store, false), "RESOURCE_NOT_FOUND");
  }

  List<String> parallel(Supplier<String> first, Supplier<String> second) throws Exception {
    var ready = new CountDownLatch(2);
    var go = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of(first, second).stream()
              .map(
                  action ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!go.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("Barrier timeout");
                            try {
                              return action.get();
                            } finally {
                              SecurityContextHolder.clearContext();
                            }
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      var result = new ArrayList<String>();
      for (var task : tasks) result.add(task.get(30, TimeUnit.SECONDS));
      return result;
    }
  }

  @Test
  void reelCaptionPersistsOnCreateAndEditWithoutLeakingIntoPublicProduct() throws Exception {
    var original = request("Reel caption test " + UUID.randomUUID());
    var input = new ProductWrite(original.categoryId(), original.title(), original.description(),
        original.price(), original.currency(), original.colour(), original.sizes(), original.material(),
        original.occasion(), original.brand(), original.stockQuantity(), original.availabilityEnabled(),
        "https://www.instagram.com/reel/Example123/", List.of("reelcraft"), original.images(),
        "Private caption with cotton and handloom");
    var created = products.create(input);
    UUID id = created.product().id();
    assertThat(products.own(id).reelCaption()).isEqualTo(input.reelCaption());
    assertThat(jdbc.queryForObject("select reel_caption from product where id=?", String.class, id))
        .isEqualTo(input.reelCaption());
    var edited = new ProductWrite(input.categoryId(), input.title(), input.description(), input.price(),
        input.currency(), input.colour(), input.sizes(), input.material(), input.occasion(), input.brand(),
        input.stockQuantity(), input.availabilityEnabled(), input.sourceUrl(), input.sellerTags(), input.images(),
        "Updated private caption");
    var saved = products.replace(id, edited, created.product().version());
    assertThat(saved.reelCaption()).isEqualTo("Updated private caption");
    products.status(id, ListingStatus.ACTIVE, saved.product().version());
    assertThat(search.search(query("reelcraft")).items()).extracting(Contracts.ProductSummary::id).contains(id);
    String publicJson = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
        .writeValueAsString(catalog.publicProduct(id));
    assertThat(publicJson).contains("https://www.instagram.com/reel/Example123/")
        .doesNotContain("reelCaption", "Updated private caption");
  }
}
