package com.instabazaar;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:http-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "instabazaar.seed-demo=true", "instabazaar.storage-path=target/http-test-uploads"})
@ActiveProfiles({"test", "demo"})
class NativeHttpIntegrationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    @Test void actualServerHandlesDetachedEntitiesSessionsCsrfAndMultipartUploads() throws Exception {
        var home = get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("Sample Loom Studio", "Demo illustration").doesNotContain("The next great find starts with you.");
        String email = "http-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID() + "Aa!";
        var registration = post("/register", Map.of("_csrf", token(get("/register").body()), "displayName", "HTTP Seller", "email", email, "password", password, "role", "SELLER"));
        assertThat(registration.statusCode()).isEqualTo(302);
        assertThat(post("/login", Map.of("_csrf", token(get("/login").body()), "email", email, "password", password)).statusCode()).isEqualTo(302);
        var editor = get("/seller/products/new");
        assertThat(editor.statusCode()).isEqualTo(200);
        var categoryMatch = Pattern.compile("<select id=\"categoryId\"[\\s\\S]*?<option value=\"(\\d+)\"").matcher(editor.body());
        assertThat(categoryMatch.find()).isTrue();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("_csrf", token(editor.body())); fields.put("title", "HTTP smoke dress");
        fields.put("description", "An actual HTTP multipart upload."); fields.put("price", "1499.00");
        fields.put("categoryId", categoryMatch.group(1)); fields.put("colour", "red"); fields.put("sizes", "S, M");
        fields.put("stock", "2"); fields.put("status", "ACTIVE"); fields.put("version", "0");
        fields.put("caption", "Private HTTP caption"); fields.put("tags", "red, dress");
        String boundary = "InstaBazaar" + UUID.randomUUID().toString().replace("-", "");
        var bytes = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) {
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"sample.webp\"\r\nContent-Type: image/webp\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA"));
        bytes.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var upload = client.send(HttpRequest.newBuilder(uri("/seller/products"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(upload.statusCode()).isEqualTo(302);
        var dashboard = get("/seller/products");
        assertThat(dashboard.body()).contains("HTTP smoke dress");
        var idMatch = Pattern.compile("/seller/products/(\\d+)/edit").matcher(dashboard.body());
        assertThat(idMatch.find()).isTrue();
        String id = idMatch.group(1);
        var product = get("/products/" + id);
        assertThat(product.statusCode()).isEqualTo(200);
        assertThat(product.body()).doesNotContain("Private HTTP caption");
        var mediaMatch = Pattern.compile("src=\"(/media/[^\"]+)\"").matcher(product.body());
        assertThat(mediaMatch.find()).isTrue();
        var media = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(mediaMatch.group(1))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(media.statusCode()).isEqualTo(200);
        assertThat(media.headers().firstValue("Content-Type")).contains("image/webp");
        assertThat(post("/wishlist/" + id, Map.of("_csrf", token(product.body()))).statusCode()).isEqualTo(302);
        assertThat(get("/wishlist").body()).contains("HTTP smoke dress");
        assertThat(get("/products?q=HTTP+smoke").body()).contains("HTTP smoke dress");
    }

    private URI uri(String route) { return URI.create("http://127.0.0.1:" + port + route); }
    private HttpResponse<String> get(String route) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(route)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String route, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(uri(route)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String token(String html) {
        var match = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertThat(match.find()).as("CSRF token must be present").isTrue();
        return match.group(1);
    }
}
