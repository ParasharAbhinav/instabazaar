package com.instabazaar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.account.service.AccountService;
import com.instabazaar.account.service.AuthenticationService;
import com.instabazaar.catalog.service.CatalogReadService;
import com.instabazaar.catalog.service.CategoryService;
import com.instabazaar.catalog.service.ProductService;
import com.instabazaar.catalog.service.TagService;
import com.instabazaar.config.SecurityConfig;
import com.instabazaar.health.dto.HealthResponse;
import com.instabazaar.health.service.HealthService;
import com.instabazaar.media.service.MediaService;
import com.instabazaar.media.storage.MediaStorage;
import com.instabazaar.moderation.service.AdminQueryService;
import com.instabazaar.moderation.service.ModerationService;
import com.instabazaar.moderation.service.ReportService;
import com.instabazaar.review.service.ReviewService;
import com.instabazaar.search.service.SearchService;
import com.instabazaar.security.SessionPrincipal;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.BusinessException;
import com.instabazaar.shared.exception.ServiceErrorMapper;
import com.instabazaar.shared.web.HttpErrors;
import com.instabazaar.storefront.service.StorefrontService;
import com.instabazaar.wishlist.service.WishlistService;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockPart;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(properties = "server.servlet.session.cookie.secure=false")
@Import({SecurityConfig.class, HttpErrors.class, ServiceErrorMapper.class, ApiWebTest.Time.class})
class ApiWebTest {
  static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000042");
  static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

  @TestConfiguration
  static class Time {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, java.time.ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean AuthenticationService authenticationService;
  @MockitoBean AccountService accounts;
  @MockitoBean ProductService products;
  @MockitoBean TagService tags;
  @MockitoBean CatalogReadService catalog;
  @MockitoBean CategoryService categories;
  @MockitoBean SearchService search;
  @MockitoBean StorefrontService stores;
  @MockitoBean MediaService media;
  @MockitoBean MediaStorage storage;
  @MockitoBean WishlistService wishlist;
  @MockitoBean ReviewService reviews;
  @MockitoBean ReportService reports;
  @MockitoBean ModerationService moderation;
  @MockitoBean AdminQueryService admin;
  @MockitoBean HealthService health;

  static Authentication actor(String role) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new SessionPrincipal(ID), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  static Contracts.User profile(String role) {
    return new Contracts.User(ID, "User", "user@example.com", role, true, 0, NOW);
  }

  static org.springframework.test.web.servlet.request.RequestPostProcessor csrfHeader() {
    return request -> {
      String token = UUID.randomUUID().toString();
      request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token));
      request.addHeader("X-CSRF-TOKEN", token);
      return request;
    };
  }

  @BeforeEach
  void fixtures() {
    when(accounts.me())
        .thenAnswer(
            invocation -> {
              String authority =
                  SecurityContextHolder.getContext()
                      .getAuthentication()
                      .getAuthorities()
                      .iterator()
                      .next()
                      .getAuthority();
              return profile(authority.substring("ROLE_".length()));
            });
    when(search.search(any())).thenReturn(new Contracts.Page<>(List.of(), 0, 20, 0, 0));
    when(categories.active()).thenReturn(new Contracts.Items<>(List.of()));
    when(health.check())
        .thenReturn(
            new HealthResponse(
                "instabazaar", "UP", new HealthResponse.Checks("UP", "APPLIED"), NOW));
  }

  static Stream<Arguments> accessMatrix() {
    return Stream.of("BUYER", "SELLER", "ADMIN")
        .flatMap(
            role ->
                Stream.of(
                    Arguments.of("/api/v1/me", role, 200),
                    Arguments.of("/api/v1/me/wishlist", role, 200),
                    Arguments.of(
                        "/api/v1/seller/products", role, role.equals("SELLER") ? 200 : 403),
                    Arguments.of("/api/v1/seller/store", role, role.equals("SELLER") ? 200 : 403),
                    Arguments.of(
                        "/api/v1/admin/statistics", role, role.equals("ADMIN") ? 200 : 403),
                    Arguments.of("/api/v1/admin/users", role, role.equals("ADMIN") ? 200 : 403),
                    Arguments.of("/api/v1/admin/products", role, role.equals("ADMIN") ? 200 : 403),
                    Arguments.of("/api/v1/admin/reviews", role, role.equals("ADMIN") ? 200 : 403),
                    Arguments.of("/api/v1/admin/reports", role, role.equals("ADMIN") ? 200 : 403)));
  }

  @ParameterizedTest
  @MethodSource("accessMatrix")
  void enforcesRoleMatrix(String route, String role, int expected) throws Exception {
    var response =
        mvc.perform(get(route).with(authentication(actor(role))))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse();
    if (expected == 403) {
      var body = json.readTree(response.getContentAsString());
      assertThat(body.path("code").asText()).isEqualTo("ACCESS_DENIED");
      assertThat(body.path("traceId").asText()).isEqualTo(response.getHeader("X-Trace-ID"));
    }
  }

  @Test
  void anonymousReadsAndHeadArePublic() throws Exception {
    for (String route : List.of("/api/v1/products", "/api/v1/categories", "/api/v1/health")) {
      mvc.perform(get(route)).andExpect(status().isOk());
    }
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(
                "/api/v1/products"))
        .andExpect(status().isOk());
  }

  @Test
  void anonymousPrivateRouteUsesJson401WithoutRedirectOrSession() throws Exception {
    var result =
        mvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(header().doesNotExist("Location"))
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    assertThat(json.readTree(result.getResponse().getContentAsString()).properties())
        .extracting(java.util.Map.Entry::getKey)
        .containsExactlyInAnyOrder("code", "message", "fieldErrors", "traceId", "timestamp");
  }

  @Test
  void writeToPublicNamespaceRequiresAuthentication() throws Exception {
    mvc.perform(
            post("/api/v1/products/" + ID + "/reviews")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/v1/reviews/" + ID)
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"version\":0}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginAndLogoutCannotBypassCsrf() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123456\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    mvc.perform(post("/api/v1/auth/logout"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  @Test
  void csrfEndpointReturnsUsableRawTokenAndReadableLaxCookie() throws Exception {
    var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    var token = json.readTree(result.getResponse().getContentAsString()).path("token").asText();
    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo(token);
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    when(authenticationService.verifyCredentials("user@example.com", "password123456"))
        .thenReturn(actor("BUYER"));
    var session = new MockHttpSession();
    String originalId = session.getId();
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .session(session)
                    .cookie(cookie)
                    .header("X-CSRF-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user@example.com\",\"password\":\"password123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("BUYER"))
            .andReturn();
    assertThat(session.getId()).isNotEqualTo(originalId);
    var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
    assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new SessionPrincipal(ID));
    assertThat(context.getAuthentication().getCredentials()).isNull();
    assertThat(login.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
    mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/logout").session(session).with(csrfHeader()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsWrongHeaderAndWrongToken() throws Exception {
    var result = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    mvc.perform(
            post("/api/v1/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/auth/logout").cookie(cookie).header("X-CSRF-TOKEN", "incorrect"))
        .andExpect(status().isForbidden());
  }

  @Test
  void credentialedPreflightUsesExactOriginAndRejectsUnknownOrigins() throws Exception {
    mvc.perform(
            options("/api/v1/me")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(
            options("/api/v1/me")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "PUT"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mvc.perform(get("/api/v1/products").header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void validatesBodiesAndRejectsUnknownFieldsWithoutEchoingPassword() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"\",\"email\":\"invalid\",\"password\":\"sensitive\",\"role\":\"BUYER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors").isNotEmpty())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"user@example.com\",\"password\":\"password\",\"admin\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
  }

  @Test
  void filterAndMvcExceptionsUseSameContractAndHideInternalDetails() throws Exception {
    when(accounts.updateName("Name", 0L))
        .thenThrow(new OptimisticLockException("database private"));
    mvc.perform(
            put("/api/v1/me")
                .with(authentication(actor("BUYER")))
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Name\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    when(catalog.publicProduct(ID)).thenThrow(new IllegalStateException("secret SQL password"));
    mvc.perform(get("/api/v1/products/" + ID))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("An internal error occurred."));
    doThrow(new BusinessException("ACCOUNT_DISABLED", 403, "Account is disabled."))
        .when(accounts)
        .me();
    var session = new MockHttpSession();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(actor("BUYER"));
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    mvc.perform(get("/api/v1/me").session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void typeBindingAndMethodErrorsAreStructured() throws Exception {
    mvc.perform(get("/api/v1/products/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get("/api/v1/products").param("size", "NaN")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products").param("ignored", "value"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    mvc.perform(post("/api/v1/categories").with(authentication(actor("BUYER"))).with(csrfHeader()))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/v1/me").with(authentication(actor("BUYER"))).with(csrfHeader()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
        .andExpect(header().exists("Allow"));
  }

  @Test
  void wishlistMutationsDelegateAndReturn204() throws Exception {
    mvc.perform(
            put("/api/v1/me/wishlist/" + ID)
                .with(authentication(actor("BUYER")))
                .with(csrfHeader()))
        .andExpect(status().isNoContent());
    verify(wishlist).save(ID);
    mvc.perform(
            delete("/api/v1/me/wishlist/" + ID)
                .with(authentication(actor("SELLER")))
                .with(csrfHeader()))
        .andExpect(status().isNoContent());
    verify(wishlist).remove(ID);
  }

  @Test
  void rejectsFractionalIntegersNumericEnumsAndDuplicateJsonKeys() throws Exception {
    mvc.perform(
            post("/api/v1/products/" + ID + "/reviews")
                .with(authentication(actor("BUYER")))
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4.5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Name\",\"email\":\"user@example.com\",\"password\":\"password123456\",\"role\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"first@example.com\",\"email\":\"last@example.com\",\"password\":\"password123456\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
  }

  @Test
  void uploadChecksPartsAndReturnsCreatedAsset() throws Exception {
    when(media.upload(any()))
        .thenReturn(new Contracts.Asset(ID, "/api/v1/media/" + ID, "image/png", 3, 1, 1));
    mvc.perform(
            multipart("/api/v1/seller/media")
                .part(new MockPart("file", "image.png", new byte[] {1, 2, 3}))
                .with(authentication(actor("SELLER")))
                .with(csrfHeader()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/media/" + ID));
    mvc.perform(
            multipart("/api/v1/seller/media")
                .part(new MockPart("file", "image.png", new byte[] {1}))
                .part(new MockPart("extra", new byte[] {2}))
                .with(authentication(actor("SELLER")))
                .with(csrfHeader()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/v1/seller/media")
                .part(new MockPart("file", "image.png", new byte[5 * 1024 * 1024 + 1]))
                .with(authentication(actor("SELLER")))
                .with(csrfHeader()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
  }

  @Test
  void mediaReadAuthorizesBeforeOpeningStorageAndNeverSerializesStorageKey() throws Exception {
    when(media.authorizeRead(ID))
        .thenReturn(new MediaService.AuthorizedMedia("private.bin", "image/png", 3));
    when(storage.read("private.bin"))
        .thenReturn(new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}));
    mvc.perform(get("/api/v1/media/" + ID))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", "no-store"));
    when(media.authorizeRead(ID)).thenThrow(BusinessException.missing());
    mvc.perform(get("/api/v1/media/" + ID)).andExpect(status().isNotFound());
  }

  @Test
  void apiErrorsRemainJsonWhenClientAcceptsOnlyXml() throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .with(authentication(actor("BUYER")))
                .accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void adviceMapsAccessDenialAndDatabaseOutagesWithoutLeakingDetails() throws Exception {
    doThrow(new org.springframework.security.access.AccessDeniedException("private detail"))
        .when(catalog)
        .publicProduct(ID);
    mvc.perform(get("/api/v1/products/" + ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("private jdbc password"))
        .when(catalog)
        .publicProduct(ID);
    mvc.perform(get("/api/v1/products/" + ID))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Service is temporarily unavailable."));
  }

  @Test
  void productUpdateRequiresVersionAndRejectsNullNestedImagesBeforeDelegation() throws Exception {
    var body =
        new java.util.HashMap<String, Object>(
            java.util.Map.ofEntries(
                java.util.Map.entry("categoryId", ID),
                java.util.Map.entry("title", "Dress"),
                java.util.Map.entry("description", "A dress."),
                java.util.Map.entry("price", "99.00"),
                java.util.Map.entry("currency", "INR"),
                java.util.Map.entry("colour", "red"),
                java.util.Map.entry("sizes", List.of()),
                java.util.Map.entry("stockQuantity", 1),
                java.util.Map.entry("availabilityEnabled", true),
                java.util.Map.entry("sellerTags", List.of()),
                java.util.Map.entry("images", List.of())));
    mvc.perform(
            put("/api/v1/seller/products/" + ID)
                .with(authentication(actor("SELLER")))
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
    body.put("version", 0);
    body.put("images", java.util.Collections.singletonList(null));
    mvc.perform(
            put("/api/v1/seller/products/" + ID)
                .with(authentication(actor("SELLER")))
                .with(csrfHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    org.mockito.Mockito.verifyNoInteractions(products);
  }

  @Test
  void disabledSessionCanStillLogoutAndClearBothCookies() throws Exception {
    doThrow(new BusinessException("ACCOUNT_DISABLED", 403, "Account is disabled."))
        .when(accounts)
        .me();
    var session = new MockHttpSession();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(actor("BUYER"));
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    var response =
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrfHeader()))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getCookie("JSESSIONID").getMaxAge()).isZero();
    assertThat(response.getCookie("XSRF-TOKEN").getMaxAge()).isZero();
    org.mockito.Mockito.verifyNoInteractions(accounts);
  }

  @Test
  void acceptsOptionalReelCaptionAndRejectsOversizedCaption() throws Exception {
    var body = new java.util.HashMap<String, Object>(java.util.Map.ofEntries(
        java.util.Map.entry("categoryId", ID), java.util.Map.entry("title", "Cotton kurta"),
        java.util.Map.entry("description", "Complete product description"), java.util.Map.entry("price", "99.00"),
        java.util.Map.entry("currency", "INR"), java.util.Map.entry("colour", "olive"),
        java.util.Map.entry("sizes", List.of("M")), java.util.Map.entry("stockQuantity", 2),
        java.util.Map.entry("availabilityEnabled", true), java.util.Map.entry("sellerTags", List.of("cotton")),
        java.util.Map.entry("images", List.of()), java.util.Map.entry("version", 0),
        java.util.Map.entry("sourceUrl", "https://www.instagram.com/reel/Example123/"),
        java.util.Map.entry("reelCaption", "Handloom cotton kurta for festive wear")));
    mvc.perform(put("/api/v1/seller/products/" + ID).with(authentication(actor("SELLER")))
        .with(csrfHeader()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
    var captured = org.mockito.ArgumentCaptor.forClass(com.instabazaar.catalog.dto.ProductWrite.class);
    verify(products).replace(org.mockito.ArgumentMatchers.eq(ID), captured.capture(), org.mockito.ArgumentMatchers.eq(0L));
    assertThat(captured.getValue().reelCaption()).isEqualTo("Handloom cotton kurta for festive wear");
    body.put("reelCaption", "x".repeat(2001));
    mvc.perform(put("/api/v1/seller/products/" + ID).with(authentication(actor("SELLER")))
        .with(csrfHeader()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("reelCaption"));
    org.mockito.Mockito.verifyNoMoreInteractions(products);
  }
}
