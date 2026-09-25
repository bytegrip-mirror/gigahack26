package com.pistonpunk.gigahack26.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    private static class ExposedCorsRegistry extends CorsRegistry {

        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    private CorsConfiguration configurationFor(String allowedOrigins) {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        new CorsConfig(allowedOrigins).addCorsMappings(registry);

        Map<String, CorsConfiguration> registered = registry.configurations();
        assertEquals(1, registered.size());
        return registered.get("/api/**");
    }

    @Test
    void theBoardOriginIsAllowedOnTheApi() {
        CorsConfiguration configuration = configurationFor("https://hack.pistonpunk.com");

        assertNotNull(configuration);
        assertEquals("https://hack.pistonpunk.com",
                configuration.checkOrigin("https://hack.pistonpunk.com"));
    }

    @Test
    void anotherOriginIsRejected() {
        CorsConfiguration configuration = configurationFor("https://hack.pistonpunk.com");

        assertNull(configuration.checkOrigin("https://not-the-board.example"));
    }

    @Test
    void thePostTheBoardSendsIsAnswered() {
        CorsConfiguration configuration = configurationFor("https://hack.pistonpunk.com");

        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedMethods().contains("POST"));
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
        assertTrue(configuration.checkHttpMethod(HttpMethod.POST).contains(HttpMethod.POST));
        assertTrue(configuration.getMaxAge() > 0);
    }

    @Test
    void severalOriginsParseThroughSpacesAndEmptyEntries() {
        CorsConfiguration configuration = configurationFor(" https://hack.pistonpunk.com , ,http://localhost:5173 ");

        assertNotNull(configuration.checkOrigin("https://hack.pistonpunk.com"));
        assertNotNull(configuration.checkOrigin("http://localhost:5173"));
        assertNull(configuration.checkOrigin("https://somewhere-else.example"));
    }
}
