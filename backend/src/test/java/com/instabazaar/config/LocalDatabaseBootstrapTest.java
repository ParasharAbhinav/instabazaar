package com.instabazaar.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.PlaceholderResolutionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalDatabaseBootstrapTest {

    @Test
    void applicationYamlEnforcesFailClosedCredentialsAndResolvesConfiguredRuntimeRole() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));

        MockEnvironment emptyEnv = new MockEnvironment();
        for (PropertySource<?> source : sources) {
            emptyEnv.getPropertySources().addLast(source);
        }
        assertThrows(
                PlaceholderResolutionException.class,
                () -> emptyEnv.getProperty("spring.datasource.username")
        );

        MockEnvironment configuredEnv = new MockEnvironment()
                .withProperty("SPRING_DATASOURCE_USERNAME", "instabazaar_runtime")
                .withProperty("SPRING_DATASOURCE_PASSWORD", "ephemeral_test_secret");
        for (PropertySource<?> source : sources) {
            configuredEnv.getPropertySources().addLast(source);
        }
        assertEquals("instabazaar_runtime", configuredEnv.getProperty("spring.datasource.username"));
        assertEquals("ephemeral_test_secret", configuredEnv.getProperty("spring.datasource.password"));
    }
}