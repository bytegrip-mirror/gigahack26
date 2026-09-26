package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shipped language-model overlay, read once and shared by everything that
 * classifies.
 *
 * It used to live inside the seeder, which meant the re-score path could not see
 * it: typing one question into the Studio re-classified the whole service line
 * with the local engine and replaced every model verdict on it. Reading the
 * overlay is now a service so a classification run over pairs the model has
 * already answered keeps the model's answer, whoever started the run.
 */
@Service
public class SignalOverlayStore {

    private static final Logger log = LoggerFactory.getLogger(SignalOverlayStore.class);

    public record Entry(String engine, Verdict verdict, double confidence, String quote, String rationale) {
    }

    private final CorpusProperties properties;
    private final ObjectMapper mapper;
    private volatile Map<String, Entry> entries;

    public SignalOverlayStore(CorpusProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public Entry find(String evidenceKey, String questionKey) {
        if (evidenceKey == null || questionKey == null) {
            return null;
        }
        return entries().get(evidenceKey + "|" + questionKey);
    }

    public boolean present() {
        return !entries().isEmpty();
    }

    public String engine() {
        for (Entry entry : entries().values()) {
            if (entry.engine() != null) {
                return entry.engine();
            }
        }
        return null;
    }

    public int size() {
        return entries().size();
    }

    /**
     * How many rows the overlay claims should exist on the board: the pairs it
     * answered with a verdict and a quote to locate. A board carrying materially
     * fewer rows than this from the overlay's engine has had its model
     * classifications replaced, which is the thing the seeder must notice.
     */
    public int expected() {
        int count = 0;
        for (Entry entry : entries().values()) {
            if (entry.verdict() != Verdict.NONE && entry.quote() != null && !entry.quote().isBlank()) {
                count++;
            }
        }
        return count;
    }

    public void reset() {
        entries = null;
    }

    private Map<String, Entry> entries() {
        Map<String, Entry> held = entries;
        if (held != null) {
            return held;
        }
        synchronized (this) {
            if (entries == null) {
                entries = load();
            }
            return entries;
        }
    }

    private Map<String, Entry> load() {
        Map<String, Entry> loaded = new LinkedHashMap<>();
        CorpusFiles.SignalOverlay parsed = read(properties.signalsPath());
        if (parsed == null || parsed.signals() == null) {
            return loaded;
        }
        String engine = parsed.engine() == null ? "language-model" : parsed.engine();
        for (CorpusFiles.OverlaySignal entry : parsed.signals()) {
            loaded.put(entry.evidenceKey() + "|" + entry.questionKey(), new Entry(
                    engine,
                    Verdict.parse(entry.verdict()),
                    entry.confidence() == null ? 0.0 : entry.confidence(),
                    entry.quote(),
                    entry.rationale()));
        }
        log.info("loaded {} pre-extracted signals from {}", loaded.size(), properties.signalsPath());
        return loaded;
    }

    private CorpusFiles.SignalOverlay read(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            ClassPathResource resource = new ClassPathResource(location);
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    return mapper.readValue(stream, CorpusFiles.SignalOverlay.class);
                }
            }
            Path path = Path.of(location);
            if (Files.exists(path)) {
                return mapper.readValue(Files.readString(path, StandardCharsets.UTF_8),
                        CorpusFiles.SignalOverlay.class);
            }
            log.info("{} is not present", location);
        } catch (Exception e) {
            log.warn("could not read {}: {}", location, e.toString());
        }
        return null;
    }
}
