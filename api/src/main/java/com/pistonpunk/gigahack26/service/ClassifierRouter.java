package com.pistonpunk.gigahack26.service;

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

    public ClassifierRouter(ClassifierProperties properties, LocalRubricEngine local, DeepSeekEngine deepSeek) {
        this.properties = properties;
        this.local = local;
        this.deepSeek = deepSeek;
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

    public List<ClassifierEngine.Finding> classify(String companyName, PolicyQuestion question, String document,
                                                   String engineOverride) {
        ClassifierEngine chosen = engine(engineOverride);
        List<ClassifierEngine.Finding> findings = chosen.classify(
                new ClassifierEngine.ClassificationRequest(companyName, question, document));

        if (findings.isEmpty() && chosen != local && !LANGUAGE_MODEL.equals(engineOverride)
                && !LANGUAGE_MODEL.equals(properties.engine())) {
            return local.classify(new ClassifierEngine.ClassificationRequest(companyName, question, document));
        }
        return findings;
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
