package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.ClassificationCache;
import com.pistonpunk.gigahack26.repository.jpa.ClassificationCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClassificationCacheService {

    private final ClassificationCacheRepository cache;
    private final Clock clock;

    public ClassificationCacheService(ClassificationCacheRepository cache, Clock clock) {
        this.cache = cache;
        this.clock = clock;
    }

    public record Entry(Verdict verdict, double confidence, int spanStart, int spanEnd, String rationale) {
    }

    public enum Verdict {
        MATCH,
        NO_MATCH
    }

    @Transactional(readOnly = true)
    public Map<String, Entry> lookup(List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, Entry> found = new LinkedHashMap<>();
        for (ClassificationCache row : cache.findByKeyIn(keys)) {
            found.put(row.getKey(), new Entry(
                    row.getVerdict() == com.pistonpunk.gigahack26.domain.Verdict.NONE
                            ? Verdict.NO_MATCH
                            : Verdict.MATCH,
                    row.getConfidence(), row.getSpanStart(), row.getSpanEnd(), row.getRationale()));
        }
        return found;
    }

    @Transactional
    public void record(String contentHash, String questionKey, String engine,
                       com.pistonpunk.gigahack26.domain.Verdict verdict, double confidence,
                       int spanStart, int spanEnd, String rationale) {
        String key = ClassificationCache.keyFor(contentHash, questionKey, engine);
        Optional<ClassificationCache> existing = cache.findByKey(key);
        if (existing.isPresent()) {
            ClassificationCache row = existing.get();
            row.hit(clock.instant());
            cache.save(row);
            return;
        }
        cache.save(new ClassificationCache(UUID.randomUUID(), key, contentHash, questionKey, engine,
                verdict == null ? com.pistonpunk.gigahack26.domain.Verdict.NONE : verdict,
                confidence, spanStart, spanEnd, rationale, clock.instant()));
    }

    @Transactional
    public void recordMiss(String contentHash, String questionKey, String engine) {
        record(contentHash, questionKey, engine, com.pistonpunk.gigahack26.domain.Verdict.NONE, 0.0, 0, 0,
                "the engine read this document and found nothing");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("entries", cache.count());
        summary.put("reused", cache.totalHits());
        summary.put("byEngine", Map.of(
                "language-model", cache.countByEngine("deepseek-flash@deepseek"),
                "local", cache.countByEngine("local-rubric-v1")));
        return summary;
    }
}
