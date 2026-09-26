package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.ClassificationCache;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassifierRouter {

    public static final String LOCAL = "local";
    public static final String LANGUAGE_MODEL = "deepseek";

    private final ClassifierProperties properties;
    private final LocalRubricEngine local;
    private final DeepSeekEngine deepSeek;
    private final ClassificationCacheService cache;

    public ClassifierRouter(ClassifierProperties properties, LocalRubricEngine local, DeepSeekEngine deepSeek,
                            ClassificationCacheService cache) {
        this.properties = properties;
        this.local = local;
        this.deepSeek = deepSeek;
        this.cache = cache;
    }

    public String requestedEngine() {
        return properties.engine();
    }

    public String localEngineId() {
        return local.id();
    }

    public String primaryEngineId() {
        return engine(properties.engine()).id();
    }

    public String engineId(String override) {
        return engine(override).id();
    }

    public boolean languageModelAvailable() {
        return deepSeek.available();
    }

    public boolean degraded() {
        return !LOCAL.equals(properties.engine()) && !deepSeek.available();
    }

    public List<ClassifierEngine.Finding> classify(String companyName, PolicyQuestion question, String document) {
        return classify(companyName, question, document, null);
    }

    /**
     * The findings and the engine that produced them, so a row can never claim the
     * local rubric answered when the language model did. Span verification is the
     * language model's own gate: the local engine reads the document itself.
     */
    public record Answer(String engineId, boolean spanVerified, List<ClassifierEngine.Finding> findings) {
    }

    public Answer answer(String companyName, PolicyQuestion question, String document, String engineOverride) {
        ClassifierEngine chosen = engine(engineOverride);
        boolean model = chosen != local;
        return new Answer(chosen.id(), !model, classify(companyName, question, document, engineOverride));
    }

    public List<ClassifierEngine.Finding> classify(String companyName, PolicyQuestion question, String document,
                                                   String engineOverride) {
        ClassifierEngine chosen = engine(engineOverride);
        ClassifierEngine.ClassificationRequest ask =
                new ClassifierEngine.ClassificationRequest(companyName, question, document);

        if (chosen == local) {
            return local.classify(ask);
        }

        String fingerprint = fingerprint(document);
        String key = ClassificationCache.keyFor(fingerprint, question.getKey(), chosen.id());
        ClassificationCacheService.Entry remembered = cache.lookup(List.of(key)).get(key);
        if (remembered != null) {
            return remembered.verdict() == ClassificationCacheService.Verdict.MATCH
                    ? List.of(new ClassifierEngine.Finding(verdictFor(question), remembered.confidence(),
                            remembered.spanStart(), remembered.spanEnd(), remembered.rationale()))
                    : List.of();
        }

        List<ClassifierEngine.Finding> findings;
        try {
            findings = chosen.classify(ask);
        } catch (LanguageModelUnavailableException e) {
            if (!LANGUAGE_MODEL.equals(engineOverride) && !LANGUAGE_MODEL.equals(properties.engine())) {
                return local.classify(ask);
            }
            throw e;
        }

        if (findings.isEmpty()) {
            if (!LANGUAGE_MODEL.equals(engineOverride) && !LANGUAGE_MODEL.equals(properties.engine())) {
                List<ClassifierEngine.Finding> fallback = local.classify(ask);
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            }
            cache.recordMiss(fingerprint, question.getKey(), chosen.id());
            return List.of();
        }

        ClassifierEngine.Finding best = findings.get(0);
        cache.record(fingerprint, question.getKey(), chosen.id(), best.verdict(), best.confidence(),
                best.spanStart(), best.spanEnd(), best.rationale());
        return findings;
    }

    public record ModelAnswer(List<ClassifierEngine.Finding> findings, boolean reused) {
    }

    public ModelAnswer askModel(String companyName, PolicyQuestion question, String document) {
        ClassifierEngine.ClassificationRequest ask =
                new ClassifierEngine.ClassificationRequest(companyName, question, document);
        String fingerprint = fingerprint(document);
        String key = ClassificationCache.keyFor(fingerprint, question.getKey(), deepSeek.id());

        ClassificationCacheService.Entry remembered = cache.lookup(List.of(key)).get(key);
        if (remembered != null) {
            return new ModelAnswer(remembered.verdict() == ClassificationCacheService.Verdict.MATCH
                    ? List.of(new ClassifierEngine.Finding(verdictFor(question), remembered.confidence(),
                            remembered.spanStart(), remembered.spanEnd(), remembered.rationale()))
                    : List.of(), true);
        }

        List<ClassifierEngine.Finding> findings = deepSeek.classify(ask);
        if (findings.isEmpty()) {
            cache.recordMiss(fingerprint, question.getKey(), deepSeek.id());
        } else {
            ClassifierEngine.Finding best = findings.get(0);
            cache.record(fingerprint, question.getKey(), deepSeek.id(), best.verdict(), best.confidence(),
                    best.spanStart(), best.spanEnd(), best.rationale());
        }
        return new ModelAnswer(findings, false);
    }

    public boolean modelAvailable() {
        return deepSeek.available();
    }

    private static com.pistonpunk.gigahack26.domain.Verdict verdictFor(
            com.pistonpunk.gigahack26.domain.PolicyQuestion question) {
        return switch (question.getPolarity()) {
            case POSITIVE -> com.pistonpunk.gigahack26.domain.Verdict.BUY;
            case NEGATIVE -> com.pistonpunk.gigahack26.domain.Verdict.COUNTER;
            case DISQUALIFYING -> com.pistonpunk.gigahack26.domain.Verdict.BUILD;
        };
    }

    static String fingerprint(String document) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "len:" + document.length();
        }
    }

    private ClassifierEngine engine(String override) {
        String name = override == null || override.isBlank() ? properties.engine() : override;
        return switch (name) {
            case LOCAL -> local;
            case LANGUAGE_MODEL -> deepSeek.available() ? deepSeek : local;
            default -> deepSeek.available() ? deepSeek : local;
        };
    }
}
