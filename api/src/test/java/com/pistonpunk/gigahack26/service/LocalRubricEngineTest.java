package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.DecayClass;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Verdict;
import com.pistonpunk.gigahack26.domain.WeightTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRubricEngineTest {

    private final ClassifierProperties properties =
            new ClassifierProperties("local", 4, 0.5, new ClassifierProperties.DeepSeek("", "", "", 1, 1000));

    private final LocalRubricEngine engine = new LocalRubricEngine(properties);

    @Test
    void aDocumentWithNoRubricTermsProducesNothingRatherThanAWeakGuess() {
        PolicyQuestion question = question(Polarity.POSITIVE, List.of("robotic process automation", "process mining"));

        List<ClassifierEngine.Finding> findings = engine.classify(new ClassifierEngine.ClassificationRequest(
                "Test Co", question, "The company published its annual report for the previous financial year."));

        assertTrue(findings.isEmpty());
    }

    @Test
    void aPassageThatAnswersTheQuestionProducesASpanThatPointsAtIt() {
        String document = "We are recruiting a process mining lead to run the automation programme across "
                + "three business units. The role reports to the chief operating officer.";
        PolicyQuestion question = question(Polarity.POSITIVE, List.of("process mining", "automation programme"));

        List<ClassifierEngine.Finding> findings = engine.classify(
                new ClassifierEngine.ClassificationRequest("Test Co", question, document));

        assertFalse(findings.isEmpty());
        ClassifierEngine.Finding finding = findings.get(0);
        assertEquals(Verdict.BUY, finding.verdict());
        assertTrue(finding.confidence() > 0.5);
        String span = document.substring(finding.spanStart(), finding.spanEnd());
        assertTrue(span.contains("process mining"), "the span should hold the phrase that matched");
    }

    @Test
    void aCounterCueSuppressesAnOtherwiseMatchingPassage() {
        String document = "We are recruiting a process mining lead for an internship programme in the summer.";
        PolicyQuestion question = question(Polarity.POSITIVE, List.of("process mining"));
        PolicyQuestion withCounter = new PolicyQuestion(
                question.getId(), question.getKey(), question.getServiceLine(), question.getText(),
                question.getInstructions(), question.getWeightTier(), question.getPolarity(), question.getTheme(),
                question.getDecayClass(), question.getCues(), List.of("internship", "summer"), true,
                Instant.parse("2026-09-25T00:00:00Z"));

        List<ClassifierEngine.Finding> without = engine.classify(
                new ClassifierEngine.ClassificationRequest("Test Co", question, document));
        List<ClassifierEngine.Finding> with = engine.classify(
                new ClassifierEngine.ClassificationRequest("Test Co", withCounter, document));

        assertFalse(without.isEmpty());
        assertTrue(with.isEmpty() || with.get(0).confidence() < without.get(0).confidence());
    }

    @Test
    void aDisqualifyingQuestionReturnsTheBuildVerdict() {
        PolicyQuestion question = new PolicyQuestion(UUID.randomUUID(), "build", PolicyLibrary.AUTOMATION,
                "Is the company building this in-house?", "Counts: in-house development.",
                WeightTier.HIGH, Polarity.DISQUALIFYING, "automation", DecayClass.STRUCTURAL,
                List.of("in-house automation", "centre of excellence"), List.of(), true,
                Instant.parse("2026-09-25T00:00:00Z"));

        String document = "The group opened an in-house automation centre of excellence in Bucharest last quarter.";

        List<ClassifierEngine.Finding> findings = engine.classify(
                new ClassifierEngine.ClassificationRequest("Test Co", question, document));

        assertFalse(findings.isEmpty());
        assertEquals(Verdict.BUILD, findings.get(0).verdict());
    }

    @Test
    void aQuestionTypedColdWithNoCuesStillMatchesOnItsOwnWording() {
        PolicyQuestion question = new PolicyQuestion(UUID.randomUUID(), "cold", PolicyLibrary.AUTOMATION,
                "Is the company consolidating shared service centres into a captive operation?",
                "", WeightTier.HIGH, Polarity.POSITIVE, "custom", DecayClass.STRUCTURAL,
                List.of(), List.of(), true, Instant.parse("2026-09-25T00:00:00Z"));

        String document = "The company is consolidating its shared service centres into one captive operation "
                + "in Cluj, moving roughly 300 roles.";

        List<ClassifierEngine.Finding> findings = engine.classify(
                new ClassifierEngine.ClassificationRequest("Test Co", question, document));

        assertFalse(findings.isEmpty(), "a question with no configured cues must still work");
    }

    @Test
    void derivedCuesIgnoreStopwordsAndShortWords() {
        List<String> cues = LocalRubricEngine.deriveCues(
                "Has the company announced an efficiency programme with a quantified target?", "");

        assertTrue(cues.contains("announced") || cues.contains("efficiency") || cues.contains("programme"));
        assertTrue(cues.stream().noneMatch(cue -> cue.length() < 6));
    }

    private PolicyQuestion question(Polarity polarity, List<String> cues) {
        return new PolicyQuestion(UUID.randomUUID(), "q", PolicyLibrary.AUTOMATION,
                "Is the company hiring for automation roles?", "Counts: automation hiring.",
                WeightTier.HIGH, polarity, "automation", DecayClass.HIRING, cues, List.of(), true,
                Instant.parse("2026-09-25T00:00:00Z"));
    }
}
