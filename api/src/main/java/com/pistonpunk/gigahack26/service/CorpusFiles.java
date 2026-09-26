package com.pistonpunk.gigahack26.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CorpusFiles {

    private static final Logger LOG = LoggerFactory.getLogger(CorpusFiles.class);

    private CorpusFiles() {
    }

    /**
     * A corpus file is read from the classpath, or from the filesystem when the
     * path names one. Both the corpus, the overlay and the seeded people arrive
     * this way, so which of the two a deployment uses stays a path, not a code
     * path.
     */
    public static <T> T read(ObjectMapper mapper, String location, Class<T> type) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            ClassPathResource resource = new ClassPathResource(location);
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    return mapper.readValue(stream, type);
                }
            }
            Path path = Path.of(location);
            if (Files.exists(path)) {
                return mapper.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
            }
            LOG.info("{} is not present", location);
        } catch (Exception e) {
            LOG.warn("could not read {}: {}", location, e.toString());
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCorpus(String generatedAt, List<RawCompany> companies, List<RawEvidence> evidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCompany(String key, String name, String domain, String country, String hqCity,
                             String industry, String sizeBand, Integer employees, Double sizeConfidence,
                             String caemCode, Boolean isCompetitor, String coverageNotes, List<String> aliases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawEvidence(String key, String companyKey, String sourceUrl, String publisher,
                              String sourceKind, String publishedAt, String observedAt, String title,
                              String text, String contentHash) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignalOverlay(String engine, String generatedAt, Double strikeRate, Integer pairsSeen,
                                List<OverlaySignal> signals) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeopleFile(String generatedAt, String note, List<RawPerson> people) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawPerson(String companyKey, String name, String role, List<RawPersonSignal> signals) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawPersonSignal(String kind, String strength, String text, String sourceUrl,
                                  String publisher, String publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverlaySignal(String evidenceKey, String questionKey, String verdict, Double confidence,
                                String quote, String rationale) {
    }
}
