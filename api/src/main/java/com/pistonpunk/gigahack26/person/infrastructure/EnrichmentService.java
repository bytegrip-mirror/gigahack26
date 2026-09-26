package com.pistonpunk.gigahack26.person.infrastructure;

import com.pistonpunk.gigahack26.person.application.EnrichmentProperties;
import com.pistonpunk.gigahack26.person.domain.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * A licensed route to the people data a crawler cannot reach.
 *
 * LinkedIn's terms forbid automated access and they enforce it, so this desk does
 * not read their pages. What it can do is ask a provider that already licenses
 * the data — People Data Labs, Apollo, Cognito — for a named person at a company
 * we already hold. The provider carries the licensing and the consent; the desk
 * carries the name and the domain, and stores only what comes back.
 *
 * With no key configured every call is a no-op that says so, the same way the
 * drafting model degrades: the demo must not depend on something that is not
 * there.
 */
@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    public record Enriched(String linkedinUrl, String headline, String seniority, String email, boolean found) {
    }

    private final EnrichmentProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public EnrichmentService(EnrichmentProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean available() {
        return properties.configured();
    }

    public String provider() {
        return properties.provider();
    }

    public Optional<Enriched> enrich(Person person, String domain) {
        if (!available() || domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("name", person.getName());
            payload.put("company", domain);
            payload.put("pretty", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("content-type", "application/json")
                    .header("X-Api-Key", properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.of(new Enriched(null, null, null, null, false));
            }
            if (response.statusCode() != 200) {
                log.warn("enrichment provider returned {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            // People Data Labs wraps the person in `data`; the others put the
            // fields at the root. Read the envelope when it is there and the body
            // when it is not, so a second provider is a new base URL rather than a
            // new parser.
            JsonNode record = root.path("data").isObject() ? root.path("data") : root;
            String linkedin = text(record, "linkedin_url");
            String headline = text(record, "job_title");
            String email = text(record, "work_email");
            if (email == null) {
                email = text(record, "recommended_personal_email");
            }
            return Optional.of(new Enriched(linkedin, headline, text(record, "job_seniority"), email,
                    linkedin != null || headline != null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("enrichment call failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
