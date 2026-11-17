package com.instabazaar.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "INSTA_INTEGRATION_TEST", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.servlet.session.cookie.secure=false")
class ApiSessionIntegrationTest {
  static final String PASSWORD = "a-test-password-at-least-twelve";
  static final String CATEGORY = "00000000-0000-4000-8000-000000000001";
  static final Path MEDIA_ROOT =
      Path.of("target", "api-media-" + UUID.randomUUID()).toAbsolutePath();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> required("TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
    properties.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
    properties.add("instabazaar.media.directory", () -> MEDIA_ROOT.toString());
  }

  static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
    return value;
  }

  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  final class Client {
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient http =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    String csrfToken;

    HttpResponse<String> request(String method, String path, Object body, int expected)
        throws Exception {
      var builder =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .header("Origin", "http://localhost:5173");
      if (!method.equals("GET") && csrfToken != null) builder.header("X-CSRF-TOKEN", csrfToken);
      builder.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      if (body != null) builder.header("Content-Type", "application/json");
      var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .as(method + " " + path + ": " + response.body())
          .isEqualTo(expected);
      return response;
    }

    JsonNode body(String method, String path, Object value, int expected) throws Exception {
      var response = request(method, path, value, expected);
      return json.readTree(response.body());
    }

    void refreshCsrf() throws Exception {
      var response = request("GET", "/api/v1/auth/csrf", null, 200);
      var token = json.readTree(response.body());
      assertThat(token.path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
      csrfToken = token.path("token").asText();
    }

    JsonNode register(String role) throws Exception {
      refreshCsrf();
      String email = UUID.randomUUID() + "@example.com";
      var user =
          body(
              "POST",
              "/api/v1/auth/register",
              Map.of("name", "API Test", "email", email, "password", PASSWORD, "role", role),
              201);
      request("GET", "/api/v1/me", null, 401); // Registration never logs in.
      return user;
    }

    void login(JsonNode registered) throws Exception {
      var response =
          request(
              "POST",
              "/api/v1/auth/login",
              Map.of("email", registered.path("email").asText(), "password", PASSWORD),
              200);
      assertThat(response.headers().allValues("set-cookie"))
          .anyMatch(
              cookie ->
                  cookie.contains("JSESSIONID=")
                      && cookie.contains("HttpOnly")
                      && cookie.contains("SameSite=Lax"));
      assertThat(response.headers().allValues("set-cookie"))
          .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && cookie.contains("Max-Age=0"));
      refreshCsrf();
      request("GET", "/api/v1/me", null, 200);
    }
  }

  @Test
  void realSessionCatalogBuyerAndAdminLifecycleUsesExistingServices() throws Exception {
    var seller = new Client();
    var buyer = new Client();
    var administrator = new Client();
    var visitor = new Client();
    var sellerUser = seller.register("SELLER");
    var buyerUser = buyer.register("BUYER");
    var adminUser = administrator.register("BUYER");
    jdbc.update(
        "update app_user set role='ADMIN' where id=?",
        UUID.fromString(adminUser.path("id").asText()));
    seller.login(sellerUser);
    buyer.login(buyerUser);
    administrator.login(adminUser);
    buyer.request("GET", "/api/v1/seller/products", null, 403);
    seller.request("GET", "/api/v1/admin/statistics", null, 403);
    var updatedUser =
        buyer.body(
            "PUT",
            "/api/v1/me",
            Map.of("name", "Updated Buyer", "version", buyerUser.path("version").asLong()),
            200);
    buyer.request("PUT", "/api/v1/me", Map.of("name", "Stale Name", "version", 0), 409);

    var store =
        seller.body(
            "PUT",
            "/api/v1/seller/store",
            Map.of("storeName", "API Store", "description", "An API test storefront."),
            201);
    visitor.request("GET", "/api/v1/sellers/" + store.path("id").asText(), null, 200);
    var created =
        seller.body(
            "POST",
            "/api/v1/seller/products",
            Map.ofEntries(
                Map.entry("categoryId", CATEGORY),
                Map.entry("title", "Red cotton dress"),
                Map.entry("description", "A cotton dress for party wear."),
                Map.entry("price", "1499.00"),
                Map.entry("currency", "INR"),
                Map.entry("colour", "red"),
                Map.entry("sizes", new String[] {"S", "M"}),
                Map.entry("stockQuantity", 3),
                Map.entry("availabilityEnabled", true),
                Map.entry("sellerTags", new String[] {"cotton"}),
                Map.entry("images", new Object[] {})),
            201);
    String productId = created.path("product").path("id").asText();
    long version = created.path("product").path("version").asLong();
    visitor.request("GET", "/api/v1/products/" + productId, null, 404);
    var tagSet =
        seller.body(
            "POST",
            "/api/v1/seller/products/" + productId + "/tag-suggestions",
            Map.of("version", version),
            200);
    assertThat(tagSet.path("items").isArray()).isTrue();
    version = tagSet.path("productVersion").asLong();
    var published =
        seller.body(
            "PATCH",
            "/api/v1/seller/products/" + productId + "/status",
            Map.of("listingStatus", "ACTIVE", "version", version),
            200);
    version = published.path("product").path("version").asLong();
    visitor.request("GET", "/api/v1/products/" + productId, null, 200);
    var results = visitor.body("GET", "/api/v1/products?q=cotton", null, 200);
    assertThat(results.path("items").size()).isGreaterThan(0);
    visitor.request("POST", "/api/v1/products/" + productId + "/reviews", Map.of("rating", 5), 403);
    seller.request("POST", "/api/v1/products/" + productId + "/reviews", Map.of("rating", 5), 422);
    buyer.request("PUT", "/api/v1/me/wishlist/" + productId, null, 204);
    buyer.request("PUT", "/api/v1/me/wishlist/" + productId, null, 204);
    var review =
        buyer.body(
            "POST",
            "/api/v1/products/" + productId + "/reviews",
            Map.of("rating", 4, "text", "Helpful product information."),
            201);
    buyer.request("POST", "/api/v1/products/" + productId + "/reviews", Map.of("rating", 5), 409);
    String reviewId = review.path("id").asText();
    var revised =
        buyer.body(
            "PUT",
            "/api/v1/reviews/" + reviewId,
            Map.of(
                "rating",
                5,
                "text",
                "Updated feedback.",
                "version",
                review.path("version").asLong()),
            200);
    administrator.request(
        "PATCH",
        "/api/v1/admin/reviews/" + reviewId + "/moderation",
        Map.of(
            "status",
            "APPROVED",
            "reason",
            "Meets publication requirements.",
            "version",
            revised.path("version").asLong()),
        200);
    var visibleReviews =
        visitor.body("GET", "/api/v1/products/" + productId + "/reviews", null, 200);
    assertThat(visibleReviews.path("items").size()).isEqualTo(1);
    var report =
        buyer.body(
            "POST",
            "/api/v1/reports",
            Map.of(
                "targetType",
                "PRODUCT",
                "targetId",
                productId,
                "reason",
                "MISLEADING",
                "details",
                "Please check the listing."),
            201);
    administrator.request(
        "PATCH",
        "/api/v1/admin/reports/" + report.path("id").asText(),
        Map.of(
            "status",
            "RESOLVED",
            "resolutionNote",
            "Listing reviewed.",
            "version",
            report.path("version").asLong()),
        200);
    administrator.request("GET", "/api/v1/admin/statistics", null, 200);
    administrator.request(
        "PATCH",
        "/api/v1/admin/products/" + productId + "/moderation",
        Map.of("moderationStatus", "HIDDEN", "reason", "Requires correction.", "version", version),
        200);
    visitor.request("GET", "/api/v1/products/" + productId, null, 404);
    var saved = buyer.body("GET", "/api/v1/me/wishlist", null, 200);
    assertThat(saved.path("items").get(0).path("visibility").asText()).isEqualTo("UNAVAILABLE");
    administrator.request(
        "PATCH",
        "/api/v1/admin/users/" + buyerUser.path("id").asText() + "/status",
        Map.of(
            "enabled",
            false,
            "reason",
            "Suspended by API test.",
            "version",
            updatedUser.path("version").asLong()),
        200);
    buyer.request("GET", "/api/v1/me", null, 403);
    buyer.request("GET", "/api/v1/me", null, 401);
    seller.request("POST", "/api/v1/auth/logout", null, 204);
    seller.request("GET", "/api/v1/me", null, 401);
  }

  @Test
  void realMultipartPreviewOwnershipAndDeletion() throws Exception {
    var seller = new Client();
    var visitor = new Client();
    var user = seller.register("SELLER");
    seller.login(user);
    var png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", png);
    String boundary = "test-" + UUID.randomUUID();
    var multipart = new ByteArrayOutputStream();
    multipart.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; "
                + "filename=\"picture.png\"\r\nContent-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    multipart.write(png.toByteArray());
    multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/seller/media"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("X-CSRF-TOKEN", seller.csrfToken)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray()))
            .build();
    var upload = seller.http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(upload.statusCode()).as(upload.body()).isEqualTo(201);
    String id = json.readTree(upload.body()).path("id").asText();
    visitor.request("GET", "/api/v1/media/" + id, null, 404);
    var preview =
        seller.http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/media/" + id))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(preview.statusCode()).isEqualTo(200);
    assertThat(preview.body()).isEqualTo(png.toByteArray());
    assertThat(preview.headers().firstValue("content-type")).contains("image/png");
    seller.request("DELETE", "/api/v1/seller/media/" + id, null, 204);
    seller.request("GET", "/api/v1/media/" + id, null, 404);
  }

  static java.util.HashMap<String, Object> productBody() {
    return new java.util.HashMap<>(
        Map.ofEntries(
            Map.entry("categoryId", CATEGORY),
            Map.entry("title", "Red cotton dress"),
            Map.entry("description", "A cotton dress for party wear."),
            Map.entry("price", "99.00"),
            Map.entry("currency", "INR"),
            Map.entry("colour", "red"),
            Map.entry("sizes", new String[] {"M"}),
            Map.entry("stockQuantity", 3),
            Map.entry("availabilityEnabled", true),
            Map.entry("sellerTags", new String[] {}),
            Map.entry("images", new Object[] {})));
  }

  @Test
  void realProductReplacementTagDecisionsAndStoreVersionsEnforceOwnership() throws Exception {
    var seller = new Client();
    var otherSeller = new Client();
    var visitor = new Client();
    seller.login(seller.register("SELLER"));
    otherSeller.login(otherSeller.register("SELLER"));
    var store =
        seller.body(
            "PUT",
            "/api/v1/seller/store",
            Map.of("storeName", "Original Store", "description", "A complete storefront."),
            201);
    seller.request("GET", "/api/v1/seller/store", null, 200);
    var changedStore =
        seller.body(
            "PUT",
            "/api/v1/seller/store",
            Map.of(
                "storeName",
                "Updated Store",
                "description",
                "An updated storefront.",
                "version",
                store.path("version").asLong()),
            200);
    assertThat(changedStore.path("version").asLong()).isGreaterThan(store.path("version").asLong());
    seller.request(
        "PUT",
        "/api/v1/seller/store",
        Map.of("storeName", "Stale Store", "description", "Stale.", "version", 0),
        409);
    var input = productBody();
    var created = seller.body("POST", "/api/v1/seller/products", input, 201);
    String id = created.path("product").path("id").asText();
    seller.request("GET", "/api/v1/seller/products", null, 200);
    seller.request("GET", "/api/v1/seller/products/" + id, null, 200);
    otherSeller.request("GET", "/api/v1/seller/products/" + id, null, 404);
    otherSeller.request("GET", "/api/v1/seller/products/" + id + "/tags", null, 404);
    seller.request("PUT", "/api/v1/seller/products/" + id, input, 400);
    input.put("version", created.path("product").path("version").asLong());
    input.put("stockQuantity", 0);
    var replaced = seller.body("PUT", "/api/v1/seller/products/" + id, input, 200);
    assertThat(replaced.path("product").path("stockQuantity").asInt()).isZero();
    seller.request("PUT", "/api/v1/seller/products/" + id, input, 409);
    otherSeller.request("PUT", "/api/v1/seller/products/" + id, input, 404);
    var suggestions =
        seller.body(
            "POST",
            "/api/v1/seller/products/" + id + "/tag-suggestions",
            Map.of("version", replaced.path("product").path("version").asLong()),
            200);
    String tagId = suggestions.path("items").get(0).path("id").asText();
    long suggestedVersion = suggestions.path("productVersion").asLong();
    otherSeller.request(
        "PATCH",
        "/api/v1/seller/products/" + id + "/tags/" + tagId,
        Map.of("status", "APPROVED", "productVersion", suggestedVersion),
        404);
    var approved =
        seller.body(
            "PATCH",
            "/api/v1/seller/products/" + id + "/tags/" + tagId,
            Map.of("status", "APPROVED", "productVersion", suggestedVersion),
            200);
    assertThat(approved.path("productVersion").asLong()).isGreaterThan(suggestedVersion);
    seller.request(
        "PATCH",
        "/api/v1/seller/products/" + id + "/tags/" + tagId,
        Map.of("status", "REJECTED", "productVersion", suggestedVersion),
        409);
    var listed = seller.body("GET", "/api/v1/seller/products/" + id + "/tags", null, 200);
    boolean foundApproved = false;
    for (var tag : listed.path("items")) {
      if (tag.path("id").asText().equals(tagId)) {
        assertThat(tag.path("status").asText()).isEqualTo("APPROVED");
        foundApproved = true;
      }
    }
    assertThat(foundApproved).isTrue();
    seller.request(
        "PATCH",
        "/api/v1/seller/products/" + id + "/status",
        Map.of("listingStatus", "ACTIVE", "version", approved.path("productVersion").asLong()),
        200);
    var detail = visitor.body("GET", "/api/v1/products/" + id, null, 200);
    assertThat(detail.path("available").asBoolean()).isFalse();
    assertThat(detail.path("seller").path("storeName").asText()).isEqualTo("Updated Store");
  }

  @Test
  void realServletMultipartLimitsAndMalformedRequestsUseApiErrors() throws Exception {
    var seller = new Client();
    seller.login(seller.register("SELLER"));
    String boundary = "limit-" + UUID.randomUUID();
    var bytes = new ByteArrayOutputStream();
    bytes.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"oversize.png\""
                + "\r\nContent-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    bytes.write(new byte[5 * 1024 * 1024 + 1]);
    bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    var upload =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/seller/media"))
            .header("X-CSRF-TOKEN", seller.csrfToken)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
            .build();
    var exceeded = seller.http.send(upload, HttpResponse.BodyHandlers.ofString());
    assertThat(exceeded.statusCode()).as(exceeded.body()).isEqualTo(413);
    assertThat(json.readTree(exceeded.body()).path("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
    var malformed =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/seller/media"))
            .header("X-CSRF-TOKEN", seller.csrfToken)
            .header("Content-Type", "multipart/form-data")
            .POST(HttpRequest.BodyPublishers.ofString("missing boundary"))
            .build();
    var rejected = seller.http.send(malformed, HttpResponse.BodyHandlers.ofString());
    assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(400);
    assertThat(json.readTree(rejected.body()).path("code").asText())
        .isEqualTo("MALFORMED_MULTIPART");
    seller.request("POST", "/api/v1/seller/media", Map.of("file", "invalid transport"), 415);
  }
}
