package com.instabazaar.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

// Explicit opt-in: never silently migrate a developer's normal database during tests.
@EnabledIfEnvironmentVariable(named = "INSTA_INTEGRATION_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> required("TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for integration tests");
        return value;
    }

    @Test
    void servesRealHttpAndReadsFlywayMigratedPostgresql() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health")).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"", "\"database\":\"UP\"", "\"migrations\":\"APPLIED\"");
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foundation_metadata", Integer.class)).isEqualTo(1);
    }

    @Test
    void returns503WithoutLeakingDetailsAndRecoversWhenFoundationIsRestored() throws Exception {
        // Dedicated opt-in test database only. Autocommit makes the failure visible to HTTP.
        jdbc.update("DELETE FROM foundation_metadata WHERE id = 1");
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health")).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("cache-control")).contains("no-store");
            assertThat(response.body()).contains("\"status\":\"DOWN\"", "\"database\":\"UP\"", "\"migrations\":\"UNAVAILABLE\"")
                .doesNotContain("password", "jdbc:", "Exception", "stackTrace");
        } finally {
            jdbc.update("INSERT INTO foundation_metadata (id, phase) VALUES (1, 'foundation') ON CONFLICT (id) DO NOTHING");
        }
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health")).GET().build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }
}
