package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Person;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider's half of the enrichment path, stood up locally.
 *
 * There is no API key in this repository and there should not be one, so the only
 * way to know the request and the parsing are right is to answer the call here in
 * the shape People Data Labs documents. What this does not prove is that their
 * field names are the ones below — if they are not, this test and the service
 * disagree with the provider together, which is one class to fix and a red test
 * rather than a silent nothing.
 */
class EnrichmentServiceTest {

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(int status, String body, AtomicReference<String> seen) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (seen != null) {
                seen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private EnrichmentService service(String baseUrl, String key) {
        return new EnrichmentService(new EnrichmentProperties("peopledatalabs", key, baseUrl), mapper);
    }

    private static Person person() {
        return new Person(UUID.randomUUID(), UUID.randomUUID(), "Bastien Parizot",
                "Senior Vice President", UUID.randomUUID(), 0, 10, "https://example.com", "Example",
                Instant.now(), Instant.now());
    }

    @Test
    void theProfileComesBackInTheFieldsTheProviderDocuments() throws IOException {
        String body = """
                {"status":200,"likelihood":9,"data":{"linkedin_url":"linkedin.com/in/bastienparizot",
                "job_title":"Senior Vice President, Global Business Services","job_seniority":"vp"}}
                """;
        EnrichmentService service = service(serve(200, body, null), "test-key");

        assertTrue(service.available());
        Optional<EnrichmentService.Enriched> found = service.enrich(person(), "reckitt.com");

        assertTrue(found.isPresent());
        assertTrue(found.get().found());
        assertEquals("linkedin.com/in/bastienparizot", found.get().linkedinUrl());
        assertEquals("Senior Vice President, Global Business Services", found.get().headline());
        assertEquals("vp", found.get().seniority());
    }

    @Test
    void theNameAndTheDomainAreWhatWeAskWith() throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        EnrichmentService service = service(serve(200, "{\"status\":200,\"data\":{}}", seen), "test-key");

        service.enrich(person(), "reckitt.com");

        String sent = seen.get();
        assertTrue(sent.contains("Bastien Parizot"), "the provider matches on the name: " + sent);
        assertTrue(sent.contains("reckitt.com"), "and on the company's domain: " + sent);
    }

    @Test
    void aProviderThatHasNeverHeardOfThemIsAnAnswerNotAFailure() throws IOException {
        EnrichmentService service = service(serve(404, "{\"status\":404}", null), "test-key");

        Optional<EnrichmentService.Enriched> found = service.enrich(person(), "reckitt.com");

        assertTrue(found.isPresent(), "a miss is a fact to store, so it is not asked for twice");
        assertFalse(found.get().found());
        assertNull(found.get().linkedinUrl());
    }

    @Test
    void aProviderThatIsDownIsNotMistakenForAMiss() throws IOException {
        EnrichmentService service = service(serve(500, "{}", null), "test-key");

        assertTrue(service.enrich(person(), "reckitt.com").isEmpty(),
                "a failure must not mark the person as having no profile");
    }

    @Test
    void withNoKeyNothingIsCalledAtAll() {
        EnrichmentService service = new EnrichmentService(
                new EnrichmentProperties("none", "", "https://api.peopledatalabs.com/v5/person/enrich"), mapper);

        assertFalse(service.available());
        assertTrue(service.enrich(person(), "reckitt.com").isEmpty(),
                "an unconfigured desk makes no request and claims nothing");
    }
}
