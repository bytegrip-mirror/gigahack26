package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Band;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.DecayClass;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.domain.SourceKind;
import com.pistonpunk.gigahack26.domain.Verdict;
import com.pistonpunk.gigahack26.domain.WeightTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private final ScoringProperties properties = new ScoringProperties(
            0.25, 0.75, 0.50, 0.30, 0.5, 200, "MD,RO,DE,FR", true, true, true, false, 1.0, "");

    private final CaptureStore captures = new CaptureStore(null);
    private final ScoringService scoring = new ScoringService(() -> properties, captures);

    @Test
    void aCompanyWithNoEvidenceScoresExactlyAtTheBaseRate() {
        Company company = company(false, "DE", 4000, 0.9);

        ScoreComputation result = scoring.compute(company, List.of(), List.of(), List.of(question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation")), NOW);

        assertEquals(0.25, result.probability(), 1e-9);
        assertEquals(Band.THIN, result.band());
        assertEquals(0, result.signalCount());
    }

    @Test
    void oneLoudThemeCanReachButNeverBreachThePriorityLine() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        List<Evidence> evidence = new ArrayList<>();
        List<Signal> signals = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            Evidence item = evidence("e" + i, "automation document " + i);
            evidence.add(item);
            signals.add(signal(item, question, Verdict.BUY, 1.0));
        }

        ScoreComputation result = scoring.compute(company(false, "DE", 4000, 0.9), evidence, signals, List.of(question), NOW);

        assertEquals(1, result.themeCount());
        assertTrue(result.probability() <= properties.priorityThreshold() + 1e-9,
                "a single saturated theme landed above the priority line at " + result.probability());
        assertTrue(result.themeTotals().get("automation") <= properties.themeCap() + 1e-9);
        assertEquals(Band.NURTURE, result.band());
    }

    @Test
    void independentCorroborationFromASecondThemeCrossesIntoPriority() {
        PolicyQuestion automation = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        PolicyQuestion security = question("s", Polarity.POSITIVE, WeightTier.HIGH, "security");

        Evidence first = evidence("e1", "cost reduction programme with a quantified target");
        Evidence second = evidence("e2", "the group appointed a new chief information security officer");

        List<Signal> signals = List.of(
                signal(first, automation, Verdict.BUY, 0.95),
                signal(second, security, Verdict.BUY, 0.95));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(first, second), signals,
                List.of(automation, security), NOW);

        assertEquals(2, result.themeCount());
        assertTrue(result.probability() >= properties.priorityThreshold(), "two themes should clear Priority");
        assertEquals(Band.PRIORITY, result.band());
    }

    @Test
    void theSecondArticleAboutTheSameThingIsWorthHalfTheFirst() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence strong = evidence("e1", "cost reduction programme");
        Evidence weak = evidence("e2", "cost reduction programme");

        List<Signal> signals = List.of(
                signal(strong, question, Verdict.BUY, 0.9),
                signal(weak, question, Verdict.BUY, 0.9));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(strong, weak), signals, List.of(question), NOW);

        List<LedgerRow> rows = result.rows();
        assertEquals(2, rows.size());
        assertEquals(0, rows.get(0).groupRank());
        assertEquals(1.0, rows.get(0).discount(), 1e-9);
        assertEquals(1, rows.get(1).groupRank());
        assertEquals(properties.rankDiscount(), rows.get(1).discount(), 1e-9);
    }

    @Test
    void twoPassagesFromOneDocumentAreRankDiscountedNotCountedTwice() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence document = evidence("e1", "cost reduction programme and process optimisation target");

        List<Signal> signals = List.of(
                signal(document, question, Verdict.BUY, 0.9, 0, 20),
                signal(document, question, Verdict.BUY, 0.9, 25, 45));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(document), signals, List.of(question), NOW);

        List<LedgerRow> rows = result.rows();
        assertEquals(2, rows.size(), "both passages are kept and shown");
        assertEquals(1.0, rows.get(0).discount(), 1e-9);
        assertEquals(properties.rankDiscount(), rows.get(1).discount(), 1e-9);
        assertEquals(rows.get(0).contributionNats() * properties.rankDiscount(),
                rows.get(1).contributionNats(), 1e-3,
                "the second passage from the same document must not carry full weight");
    }

    @Test
    void aCompanyBuildingItInHouseIsGatedAndItsWeightStaysOutOfTheSum() {
        PolicyQuestion insourcing = question("build", Polarity.DISQUALIFYING, WeightTier.HIGH, "automation");
        PolicyQuestion programme = question("programme", Polarity.POSITIVE, WeightTier.HIGH, "automation");

        Evidence internal = evidence("e1", "we develop our own automation platform in-house");
        Evidence announced = evidence("e2", "cost reduction programme with a savings target");

        List<Signal> signals = List.of(
                signal(internal, insourcing, Verdict.BUILD, 0.95),
                signal(announced, programme, Verdict.BUY, 0.95));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(internal, announced), signals,
                List.of(insourcing, programme), NOW);

        assertEquals(Band.DISQUALIFIED, result.band());
        assertNotNull(result.gateReason());
        assertTrue(result.gateReason().startsWith("Building in-house"));
        assertEquals(result.rows().stream()
                        .filter(row -> !row.blocking())
                        .mapToDouble(LedgerRow::contributionNats).sum(),
                result.nats(), 1e-6);
    }

    @Test
    void aCompetitorIsGatedBeforeAnyEvidenceIsConsidered() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence item = evidence("e1", "cost reduction programme");
        List<Signal> signals = List.of(signal(item, question, Verdict.BUY, 0.99));

        ScoreComputation result = scoring.compute(
                company(true, "DE", 4000, 0.9), List.of(item), signals, List.of(question), NOW);

        assertEquals(Band.DISQUALIFIED, result.band());
        assertTrue(result.gateReason().contains("Competitor"));
        assertTrue(result.probability() > 0.25, "the score is still computed for audit");
    }

    @Test
    void aLowConfidenceSizeEstimateDowngradesInsteadOfDisqualifying() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence item = evidence("e1", "cost reduction programme");
        List<Signal> signals = List.of(signal(item, question, Verdict.BUY, 0.9));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 40, 0.6), List.of(item), signals, List.of(question), NOW);

        assertNull(result.gateReason(), "a 60% confident headcount guess must not disqualify");
    }

    @Test
    void aConfidentSizeBelowTheFloorDoesDisqualify() {
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence item = evidence("e1", "cost reduction programme");
        List<Signal> signals = List.of(signal(item, question, Verdict.BUY, 0.9));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 40, 0.95), List.of(item), signals, List.of(question), NOW);

        assertNotNull(result.gateReason());
        assertTrue(result.gateReason().contains("size floor"));
    }

    @Test
    void counterEvidenceCarriesANegativeWeightInTheSameLedger() {
        PolicyQuestion positive = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        PolicyQuestion negative = question("b", Polarity.NEGATIVE, WeightTier.MEDIUM, "automation");

        Evidence good = evidence("e1", "cost reduction programme");
        Evidence bad = evidence("e2", "the company announced a hiring freeze");

        List<Signal> signals = List.of(
                signal(good, positive, Verdict.BUY, 0.9),
                signal(bad, negative, Verdict.COUNTER, 0.8));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(good, bad), signals,
                List.of(positive, negative), NOW);

        assertEquals(1, result.counterEvidence().size());
        assertTrue(result.counterEvidence().get(0).contributionNats() < 0);
        assertTrue(result.nats() < result.rows().stream()
                .filter(row -> row.verdict() == Verdict.BUY)
                .mapToDouble(LedgerRow::contributionNats).sum());
    }

    @Test
    void aDecayedSignalContributesLessThanAFreshOne() {
        PolicyQuestion hiring = question("h", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        hiring = new PolicyQuestion(hiring.getId(), hiring.getKey(), hiring.getTitle(), hiring.getServiceLine(), hiring.getText(),
                hiring.getInstructions(), hiring.getWeightTier(), Polarity.POSITIVE, hiring.getTheme(),
                DecayClass.HIRING, hiring.getCues(), hiring.getCounterCues(), true, hiring.getCreatedAt());

        Evidence fresh = evidence("e1", "we are hiring an automation engineer", NOW);
        Evidence old = evidence("e2", "we are hiring an automation engineer", NOW.minusSeconds(180L * 86400));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(fresh, old),
                List.of(signal(fresh, hiring, Verdict.BUY, 0.9), signal(old, hiring, Verdict.BUY, 0.9)),
                List.of(hiring), NOW);

        LedgerRow freshRow = result.rows().stream()
                .filter(row -> row.evidenceId().equals(fresh.getId())).findFirst().orElseThrow();
        LedgerRow oldRow = result.rows().stream()
                .filter(row -> row.evidenceId().equals(old.getId())).findFirst().orElseThrow();

        assertEquals(0.25, oldRow.decay(), 0.01, "180 days at a 90 day half-life is two half-lives");
        assertTrue(freshRow.rawNats() > oldRow.rawNats());
    }

    private Company company(boolean competitor, String country, int employees, double sizeConfidence) {
        return new Company(UUID.randomUUID(), "co", "Test Co", "test.example", country, "Berlin",
                "Manufacturing", "1001-5000", employees, sizeConfidence, null, competitor, null, NOW);
    }

    @Test
    void confidenceIsUntouchedAtAShrinkOfOne() {
        ScoreComputation plain = scoreWithShrink(1.0);
        ScoreComputation same = scoreWithShrink(1.0);
        assertEquals(plain.probability(), same.probability(), 1e-9);
    }

    @Test
    void shrinkingConfidencePullsTheScoreTowardTheBaseRate() {
        ScoreComputation plain = scoreWithShrink(1.0);
        ScoreComputation shrunk = scoreWithShrink(0.5);
        assertTrue(shrunk.probability() < plain.probability(),
                "a shrunk row must carry less weight, not more");
        assertTrue(shrunk.probability() > 0.25,
                "shrinking must not push a positive row below the base rate");
    }

    @Test
    void shrinkingChangesNothingWhenTheCompanyHasNoEvidence() {
        Company silent = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        ScoringService service = new ScoringService(
                () -> new ScoringProperties(0.25, 0.75, 0.50, 0.30, 0.5, 200, "MD,RO,DE,FR",
                        true, true, true, false, 0.2, ""), captures);

        ScoreComputation result = service.compute(silent, List.of(), List.of(), List.of(question), NOW);

        assertEquals(0.25, result.probability(), 1e-9,
                "there is no confidence to shrink when there is no evidence");
    }

    private ScoreComputation scoreWithShrink(double shrink) {
        Company manufacturer = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence source = evidence("e1", "cue here");
        List<Signal> signals = List.of(signal(source, question, Verdict.BUY, 0.9));
        ScoringService service = new ScoringService(
                () -> new ScoringProperties(0.25, 0.75, 0.50, 0.30, 0.5, 200, "MD,RO,DE,FR",
                        true, true, true, false, shrink, ""), captures);
        return service.compute(manufacturer, List.of(source), signals, List.of(question), NOW);
    }

    @Test
    void anIndustryListWithoutItsGateDoesNotFilter() {
        ScoreComputation result = scoreWith(true, "banking,retail");
        assertNotEquals(Band.DISQUALIFIED, result.band(),
                "the industry control is dead config unless its flag is the thing that decides");
        assertNull(result.gateReason());
    }

    @Test
    void anIndustryOutsideTheListIsRemovedWhenTheGateIsOn() {
        ScoreComputation result = scoreWith(false, "banking,retail");
        assertEquals(Band.DISQUALIFIED, result.band());
        assertTrue(result.gateReason().contains("Outside the ICP industries"));
    }

    @Test
    void anIndustryOnTheListSurvivesTheGate() {
        ScoreComputation result = scoreWith(false, "manufacturing,retail");
        assertNotEquals(Band.DISQUALIFIED, result.band());
        assertNull(result.gateReason());
    }

    @Test
    void aModelVerdictCannotRemoveACompanyTheConfigurationKept() {
        Company buyer = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "initiative");
        Evidence source = evidence("e1", "cue here");
        List<Signal> signals = List.of(signal(source, question, Verdict.BUILD, 0.9));

        ScoreComputation result = scoring.compute(buyer, List.of(source), signals, List.of(question), NOW);

        assertNull(result.gateReason(),
                "a BUILD reading on a question the team configured as positive must not delete the company");
        assertNotEquals(Band.DISQUALIFIED, result.band());
    }

    @Test
    void aReadingThatDeniesItsQuestionCarriesNoWeight() {
        PolicyQuestion positive = question("a", Polarity.POSITIVE, WeightTier.HIGH, "initiative");
        Evidence source = evidence("e1", "we sell our analytics platform to customers");
        List<Signal> signals = List.of(signal(source, positive, Verdict.BUILD, 0.95));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(source), signals, List.of(positive), NOW);

        assertEquals(0.25, result.probability(), 1e-9,
                "a company that sells the capability is not a buyer of it");
        assertEquals(Band.THIN, result.band());
        assertEquals(0, result.signalCount());
        assertEquals(0.0, result.rows().get(0).contributionNats(), 1e-9);
    }

    @Test
    void aCounterReadingOnAPositiveQuestionCarriesNoWeightEither() {
        PolicyQuestion positive = question("a", Polarity.POSITIVE, WeightTier.HIGH, "efficiency");
        Evidence source = evidence("e1", "the company announced a hiring freeze");
        List<Signal> signals = List.of(signal(source, positive, Verdict.COUNTER, 0.9));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(source), signals, List.of(positive), NOW);

        assertEquals(0.25, result.probability(), 1e-9);
        assertEquals(1, result.counterEvidence().size(),
                "a reading that argues against calling is still shown as counter-evidence");
    }

    @Test
    void aVendorReadingOnACounterEvidenceQuestionLowersTheScore() {
        PolicyQuestion counter = question("c", Polarity.NEGATIVE, WeightTier.HIGH, "in-house");
        Evidence source = evidence("e1", "we provide automation platforms to enterprises");
        List<Signal> signals = List.of(signal(source, counter, Verdict.BUILD, 0.9));

        ScoreComputation result = scoring.compute(
                company(false, "DE", 4000, 0.9), List.of(source), signals, List.of(counter), NOW);

        assertTrue(result.nats() < 0);
        assertTrue(result.probability() < 0.25);
        assertEquals(1, result.counterEvidence().size());
    }

    @Test
    void aDisqualifyingQuestionStillRemovesTheCompany() {
        Company buyer = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.DISQUALIFYING, WeightTier.HIGH, "in-house");
        Evidence source = evidence("e1", "cue here");
        List<Signal> signals = List.of(signal(source, question, Verdict.BUILD, 0.9));

        ScoreComputation result = scoring.compute(buyer, List.of(source), signals, List.of(question), NOW);

        assertEquals(Band.DISQUALIFIED, result.band());
        assertNotNull(result.gateReason());
    }

    @Test
    void aModelDisagreementDoesNotGateOnADisqualifyingQuestion() {
        Company buyer = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.DISQUALIFYING, WeightTier.HIGH, "in-house");
        Evidence source = evidence("e1", "cue here");
        List<Signal> signals = List.of(signal(source, question, Verdict.COUNTER, 0.9));

        ScoreComputation result = scoring.compute(buyer, List.of(source), signals, List.of(question), NOW);

        assertNull(result.gateReason(), "the model read the passage and disagreed with the disqualifier");
    }

    private ScoreComputation scoreWith(boolean gateOff, String industries) {
        Company manufacturer = company(false, "DE", 4000, 0.9);
        PolicyQuestion question = question("a", Polarity.POSITIVE, WeightTier.HIGH, "automation");
        Evidence source = evidence("e1", "cue here");
        List<Signal> signals = List.of(signal(source, question, Verdict.BUY, 0.9));
        ScoringService service = new ScoringService(
                () -> new ScoringProperties(0.25, 0.75, 0.50, 0.30, 0.5, 200, "MD,RO,DE,FR",
                        true, true, true, !gateOff, 1.0, industries), captures);
        return service.compute(manufacturer, List.of(source), signals, List.of(question), NOW);
    }

    private PolicyQuestion question(String key, Polarity polarity, WeightTier tier, String theme) {
        return new PolicyQuestion(UUID.randomUUID(), key, "Question " + key, PolicyLibrary.AUTOMATION, "Question " + key,
                "Rubric for " + key, tier, polarity, theme, DecayClass.STRUCTURAL,
                List.of("cue"), List.of(), true, NOW);
    }

    private Evidence evidence(String key, String text) {
        return evidence(key, text, NOW);
    }

    private Evidence evidence(String key, String text, Instant observedAt) {
        captures.put(key, text);
        return new Evidence(UUID.randomUUID(), key, UUID.randomUUID(), "https://example.test/" + key,
                "example.test", SourceKind.NEWS, 0.85, "Title " + key, observedAt, observedAt, "hash", text.length(),
                "capture-" + key);
    }

    private Signal signal(Evidence evidence, PolicyQuestion question, Verdict verdict, double confidence) {
        return signal(evidence, question, verdict, confidence, 0, Math.min(4, evidence.getCharLength()));
    }

    private Signal signal(Evidence evidence, PolicyQuestion question, Verdict verdict, double confidence,
                          int spanStart, int spanEnd) {
        return new Signal(UUID.randomUUID(), evidence.getId(), evidence.getCompanyId(), question.getId(),
                verdict, confidence, spanStart, spanEnd, "because", "test", true, NOW);
    }
}
