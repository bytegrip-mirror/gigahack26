package com.pistonpunk.gigahack26.board.application;

import com.pistonpunk.gigahack26.classification.application.ClassifierEngine;
import com.pistonpunk.gigahack26.classification.application.ClassifierRouter;
import com.pistonpunk.gigahack26.classification.application.LocalRubricEngine;
import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.domain.CompanyAlias;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyAliasRepository;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.evidence.domain.Evidence;
import com.pistonpunk.gigahack26.evidence.domain.Signal;
import com.pistonpunk.gigahack26.evidence.infrastructure.CaptureStore;
import com.pistonpunk.gigahack26.evidence.infrastructure.EvidenceRepository;
import com.pistonpunk.gigahack26.evidence.infrastructure.SignalRepository;
import com.pistonpunk.gigahack26.lead.application.LeadService;
import com.pistonpunk.gigahack26.lead.application.LeadView;
import com.pistonpunk.gigahack26.lead.domain.Lead;
import com.pistonpunk.gigahack26.person.application.PersonService;
import com.pistonpunk.gigahack26.policy.domain.PolicyQuestion;
import com.pistonpunk.gigahack26.region.application.RegionService;
import com.pistonpunk.gigahack26.scoring.application.ScoreboardService;
import com.pistonpunk.gigahack26.scoring.application.ScoringConfig;
import com.pistonpunk.gigahack26.scoring.application.ScoringService;
import com.pistonpunk.gigahack26.scoring.application.WhyNowService;
import com.pistonpunk.gigahack26.scoring.domain.Band;
import com.pistonpunk.gigahack26.scoring.domain.LedgerRow;
import com.pistonpunk.gigahack26.scoring.domain.ScoreComputation;
import com.pistonpunk.gigahack26.scoring.infrastructure.PolicyScoreRepository;
import com.pistonpunk.gigahack26.settings.application.IcpProfileService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private final CompanyRepository companies;
    private final CompanyAliasRepository aliases;
    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final PolicyScoreRepository scores;
    private final ScoreboardService scoreboard;
    private final ScoringService scoringService;
    private final WhyNowService whyNow;
    private final LeadService leads;
    private static final double REVIEW_FLOOR = 0.2;
    private static final int MAX_REVIEW_ROWS = 12;

    private final ClassifierRouter router;
    private final LocalRubricEngine localEngine;
    private final CaptureStore captureStore;
    private final ScoringConfig settings;
    private final RegionService regions;
    private final IcpProfileService icpProfiles;
    private final PersonService people;
    private final Clock clock;

    public BoardService(CompanyRepository companies, CompanyAliasRepository aliases, EvidenceRepository evidence,
                        SignalRepository signals, PolicyScoreRepository scores, ScoreboardService scoreboard,
                        ScoringService scoringService, WhyNowService whyNow, LeadService leads,
                        ClassifierRouter router, LocalRubricEngine localEngine, CaptureStore captureStore,
                        ScoringConfig settings, RegionService regions, IcpProfileService icpProfiles,
                        PersonService people, Clock clock) {
        this.companies = companies;
        this.aliases = aliases;
        this.evidence = evidence;
        this.signals = signals;
        this.scores = scores;
        this.scoreboard = scoreboard;
        this.scoringService = scoringService;
        this.whyNow = whyNow;
        this.leads = leads;
        this.router = router;
        this.localEngine = localEngine;
        this.captureStore = captureStore;
        this.settings = settings;
        this.regions = regions;
        this.icpProfiles = icpProfiles;
        this.people = people;
        this.clock = clock;
    }

    public List<BoardRow> board(String serviceLine, String filter, String sort, String query) {
        return board(serviceLine, filter, sort, query, null, null);
    }

    public List<BoardRow> board(String serviceLine, String filter, String sort, String query, String stage) {
        return board(serviceLine, filter, sort, query, stage, null);
    }

    @Cacheable(value = "board",
            key = "#serviceLine + '|' + #filter + '|' + #sort + '|' + #query + '|' + #stage + '|' + #region")
    @Transactional(readOnly = true)
    public List<BoardRow> board(String serviceLine, String filter, String sort, String query, String stage,
                                String region) {
        List<ScoreboardService.Computed> computed = scoreboard.compute(serviceLine);
        if (computed.isEmpty()) {
            return List.of();
        }

        List<Company> companyList = companies.findAll();
        Map<UUID, Company> companiesById = companyList.stream()
                .collect(Collectors.toMap(Company::getId, company -> company));
        Map<UUID, List<Evidence>> evidenceByCompany = evidence.findAll().stream()
                .collect(Collectors.groupingBy(Evidence::getCompanyId));
        Map<UUID, List<Signal>> signalsByCompany = signals.findAll().stream()
                .collect(Collectors.groupingBy(Signal::getCompanyId));
        Map<UUID, Lead> leadsByCompany = leads.byCompany(serviceLine);
        Map<UUID, List<String>> profiles = icpProfiles.membership(companyList);
        Instant now = clock.instant();

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<BoardRow> rows = new ArrayList<>();

        for (ScoreboardService.Computed entry : computed) {
            Company company = companiesById.get(entry.companyId());
            if (company == null) {
                continue;
            }
            if (!needle.isEmpty() && !matches(company, needle)) {
                continue;
            }

            ScoreComputation detail = entry.detail();
            if (!passesFilter(detail.band(), filter)) {
                continue;
            }
            Lead companyLead = leadsByCompany.get(company.getId());
            if (!passesStage(companyLead, stage)) {
                continue;
            }
            if (!inRegion(company, region)) {
                continue;
            }

            List<Evidence> companyEvidence = evidenceByCompany.getOrDefault(company.getId(), List.of());
            rows.add(toRow(company, detail, companyEvidence,
                    signalsByCompany.getOrDefault(company.getId(), List.of()),
                    leadsByCompany.get(company.getId()), now, profiles));
        }

        rows.sort(comparator(sort));
        List<BoardRow> ranked = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ranked.add(withRank(rows.get(i), i + 1));
        }
        return ranked;
    }

    public Map<String, Integer> bandCounts(String serviceLine) {
        List<ScoreboardService.Computed> computed = scoreboard.compute(serviceLine);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Band band : Band.values()) {
            counts.put(band.name(), 0);
        }
        counts.put("ALL", computed.size());
        for (ScoreboardService.Computed entry : computed) {
            counts.merge(entry.detail().band().name(), 1, Integer::sum);
        }
        counts.put("HOT", counts.get(Band.PRIORITY.name()) + counts.get(Band.NURTURE.name()));
        return counts;
    }

    @Transactional(readOnly = true)
    public Optional<CompanyDetail> detail(UUID companyId, String serviceLine) {
        Optional<Company> found = companies.findById(companyId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Company company = found.get();

        List<Evidence> companyEvidence = evidence.findByCompanyId(companyId);
        List<Signal> companySignals = signals.findByCompanyId(companyId);
        List<PolicyQuestion> active =
                scoreboard.activeQuestions(serviceLine);
        Instant now = clock.instant();

        ScoreComputation detail =
                scoringService.compute(company, companyEvidence, companySignals, active, now);

        List<String> aliasList = aliases.findByCompanyIdIn(List.of(companyId)).stream()
                .map(CompanyAlias::getAlias)
                .toList();

        Map<UUID, List<Signal>> signalsByEvidence = companySignals.stream()
                .collect(Collectors.groupingBy(Signal::getEvidenceId));
        Map<UUID, String> questionText = active.stream()
                .collect(Collectors.toMap(question -> question.getId(), question -> question.getText(), (a, b) -> a));
        Map<UUID, LedgerRow> topRowByEvidence = new LinkedHashMap<>();
        for (LedgerRow row : detail.rows()) {
            topRowByEvidence.merge(row.evidenceId(), row,
                    (a, b) -> Math.abs(a.contributionNats()) >= Math.abs(b.contributionNats()) ? a : b);
        }

        List<CompanyDetail.EvidenceView> evidenceViews = companyEvidence.stream()
                .map(item -> new CompanyDetail.EvidenceView(
                        item.getId(),
                        item.getTitle(),
                        item.getSourceUrl(),
                        item.getPublisher(),
                        item.getSourceKind().name(),
                        item.getSourceAuthority(),
                        item.getPublishedAt(),
                        item.getObservedAt(),
                        item.decayAnchorKind(),
                        item.getCharLength(),
                        item.getContentHash(),
                        (int) companySignals.stream()
                                .filter(signal -> signal.getEvidenceId().equals(item.getId())).count(),
                        Optional.ofNullable(topRowByEvidence.get(item.getId()))
                                .map(row -> row.verdict().name()).orElse(null),
                        Optional.ofNullable(topRowByEvidence.get(item.getId()))
                                .map(LedgerRow::contributionNats).orElse(null),
                        Optional.ofNullable(topRowByEvidence.get(item.getId()))
                                .map(row -> questionText.get(row.questionId())).orElse(null)))
                .sorted(Comparator.comparing(CompanyDetail.EvidenceView::observedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<CompanyDetail.Theme> themes = detail.themeTotals().entrySet().stream()
                .map(entry -> new CompanyDetail.Theme(
                        entry.getKey(),
                        entry.getValue(),
                        Math.abs(entry.getValue()) >= settings.effective().themeCap() - 1e-6,
                        (int) detail.rows().stream()
                                .filter(row -> row.theme().equals(entry.getKey())).count()))
                .toList();

        return Optional.of(new CompanyDetail(
                company.getId(),
                company.getName(),
                company.getDomain(),
                company.getCountry(),
                company.getHqCity(),
                company.getIndustry(),
                company.getSizeBand(),
                company.getEmployees(),
                company.getSizeConfidence(),
                company.getCaemCode(),
                company.isCompetitor(),
                belowFloor(company, active, companyEvidence),
                aliasList,
                company.getCoverageNotes(),
                serviceLine,
                detail.probability(),
                detail.logit(),
                detail.nats(),
                detail.band().name(),
                detail.band().label(),
                detail.gateReason(),
                detail.gateLine(),
                settings.effective().baseRate(),
                round(settings.effective().baseLogit()),
                round(settings.effective().themeCap()),
                settings.effective().priorityThreshold(),
                settings.effective().nurtureThreshold(),
                settings.effective().watchThreshold(),
                detail.signalCount(),
                detail.themeCount(),
                detail.scannedDocuments(),
                detail.silentDocuments(),
                detail.rows(),
                detail.counterEvidence(),
                themes,
                icpProfiles.membership(List.of(company)).getOrDefault(company.getId(), List.of()),
                people.contactsFor(company.getId()).stream()
                        .map(person -> new CompanyDetail.Contact(
                                person.getId(), person.getName(), person.getRole(), person.getEmail(),
                                person.getLinkedinUrl(), person.getStatus().name(), person.getStatus().label(),
                                person.getContactCount(), person.getLastContactedAt(), person.getSourceUrl()))
                        .toList(),
                detail.weakestLink(),
                detail.priorityHorizonDays(),
                detail.nurtureHorizonDays(),
                detail.sensitivity(),
                evidenceViews,
                now,
                router.primaryEngineId(),
                whyNow.explain(detail, settings.effective()),
                leads.find(companyId, serviceLine).map(lead -> new LeadView(
                        lead.getId(), lead.getCompanyId(), company.getName(), company.getDomain(),
                        lead.getServiceLine(), lead.getStatus().name(), lead.getStatus().label(),
                        lead.getChannel(), lead.getLastContactedAt(), lead.getContactCount(), lead.getNote(),
                        lead.getOutreachSubject(), null, null, lead.getCrmSyncedAt(), lead.getUpdatedAt()))
                        .orElse(null),
                leads.crmConfigured()));
    }

    public String primaryEngine() {
        return router.primaryEngineId();
    }

    public String requestedEngine() {
        return router.requestedEngine();
    }

    public boolean degraded() {
        return router.degraded();
    }

    public boolean languageModelAvailable() {
        return router.languageModelAvailable();
    }

    public String localEngineId() {
        return router.localEngineId();
    }

    private static final int SPARK_MONTHS = 12;

    /**
     * How many readings arrived in each of the last twelve months, oldest first.
     *
     * It counts rather than weighs: the chart answers "is anything still happening
     * here", and a month that produced one very strong reading and a month that
     * produced one ordinary one are the same answer to that question. The weights
     * are on the signals list, one click away.
     */
    private static List<Double> spark(List<LedgerRow> rows, Instant now) {
        double[] months = new double[SPARK_MONTHS];
        for (LedgerRow row : rows) {
            long days = Duration.between(row.anchor(), now).toDays();
            int back = (int) Math.max(0, days / 30);
            if (back >= SPARK_MONTHS) {
                continue;
            }
            months[SPARK_MONTHS - 1 - back] += 1;
        }
        List<Double> out = new ArrayList<>(SPARK_MONTHS);
        for (double month : months) {
            out.add(month);
        }
        return out;
    }

    private BoardRow toRow(Company company, ScoreComputation detail, List<Evidence> companyEvidence,
                           List<Signal> companySignals, Lead lead, Instant now,
                           Map<UUID, List<String>> profiles) {
        List<LedgerRow> ranked = detail.rows().stream()
                .filter(row -> !row.blocking() && row.asserted())
                .sorted(Comparator.comparingDouble((LedgerRow row) -> Math.abs(row.contributionNats())).reversed())
                .toList();

        List<BoardRow.Chip> chips = ranked.stream().limit(6).map(BoardService::toChip).toList();
        BoardRow.Chip strongest = chips.isEmpty() ? null : chips.get(0);
        String engine = ranked.stream()
                .collect(Collectors.groupingBy(LedgerRow::engine, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        WhyNowService.Brief why = whyNow.brief(detail, settings.effective());

        Instant newest = ranked.stream()
                .map(LedgerRow::anchor)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new BoardRow(
                company.getId(),
                0,
                company.getName(),
                company.getDomain(),
                company.getCountry(),
                company.getHqCity(),
                company.getIndustry(),
                company.getSizeBand(),
                company.isCompetitor(),
                detail.probability(),
                detail.band().name(),
                detail.band().label(),
                detail.band().shortLabel(),
                detail.gateReason(),
                detail.nats(),
                detail.signalCount(),
                detail.themeCount(),
                companyEvidence.size(),
                detail.scannedDocuments(),
                detail.silentDocuments(),
                newest,
                newest == null ? null : round(Duration.between(newest, now).toHours() / 24.0),
                spark(ranked, now),
                strongest,
                chips,
                new ArrayList<>(detail.themeTotals().keySet()),
                profiles.getOrDefault(company.getId(), List.of()),
                engine,
                lead == null ? null : lead.getStatus().name(),
                lead == null ? null : lead.getStatus().label(),
                lead == null ? null : lead.getLastContactedAt(),
                lead == null ? 0 : lead.getContactCount(),
                why.urgency(),
                why.headline());
    }

    /**
     * The passages the floor kept out, so the floor can be argued with.
     *
     * Run on the local engine whatever answered the board: this is not a
     * competing verdict, it is the near-miss list — the sentences that were
     * close enough to be worth a person's glance and not close enough to count.
     * Capped, because a queue nobody can read is the same as no queue.
     */
    private List<CompanyDetail.BelowFloor> belowFloor(Company company,
                                                      List<PolicyQuestion> questions,
                                                      List<Evidence> companyEvidence) {
        List<CompanyDetail.BelowFloor> out = new ArrayList<>();
        for (Evidence item : companyEvidence) {
            String document = captureStore.text(item.getKey());
            if (document == null || document.isBlank()) {
                continue;
            }
            for (PolicyQuestion question : questions) {
                LocalRubricEngine.Outcome outcome = localEngine.classifyDetailed(
                        new ClassifierEngine.ClassificationRequest(company.getName(), question, document));
                for (ClassifierEngine.Finding finding : outcome.rejected()) {
                    if (finding.confidence() < REVIEW_FLOOR) {
                        continue;
                    }
                    int start = Math.max(0, Math.min(finding.spanStart(), document.length()));
                    int end = Math.max(start, Math.min(finding.spanEnd(), document.length()));
                    if (end - start < 2) {
                        continue;
                    }
                    out.add(new CompanyDetail.BelowFloor(
                            item.getId().toString(), question.getKey(), question.getTitle(),
                            question.getPolarity().name(), round(finding.confidence()),
                            start, end, document.substring(start, end), finding.rationale()));
                }
            }
        }
        out.sort(Comparator.comparingDouble(CompanyDetail.BelowFloor::confidence).reversed());
        return out.size() > MAX_REVIEW_ROWS ? List.copyOf(out.subList(0, MAX_REVIEW_ROWS)) : List.copyOf(out);
    }

    private static BoardRow.Chip toChip(LedgerRow row) {
        return new BoardRow.Chip(
                row.evidenceId().toString(),
                row.questionKey(),
                row.questionTitle(),
                row.questionText(),
                row.title(),
                row.theme(),
                row.tier(),
                row.polarity(),
                row.contributionNats(),
                trim(row.spanText(), 260),
                row.spanStart(),
                row.spanEnd(),
                row.sourceUrl(),
                row.publisher());
    }


    private boolean inRegion(Company company, String region) {
        if (region == null || region.isBlank() || region.equalsIgnoreCase("world")) {
            return true;
        }
        java.util.Set<String> codes = regions.countriesIn(region);
        if (codes.isEmpty()) {
            return true;
        }
        return company.getCountry() != null && codes.contains(company.getCountry().toUpperCase(java.util.Locale.ROOT));
    }

    private static boolean passesStage(Lead lead, String stage) {
        if (stage == null || stage.isBlank() || stage.equalsIgnoreCase("any")) {
            return true;
        }
        String current = lead == null ? "NEW" : lead.getStatus().name();
        if (stage.equalsIgnoreCase("worked")) {
            return lead != null && !current.equals("NEW");
        }
        if (stage.equalsIgnoreCase("untouched")) {
            return lead == null || current.equals("NEW");
        }
        return current.equalsIgnoreCase(stage);
    }

    private static boolean passesFilter(Band band, String filter) {
        if (filter == null || filter.isBlank() || filter.equalsIgnoreCase("all")) {
            return true;
        }
        return switch (filter.toLowerCase(Locale.ROOT)) {
            case "hot" -> band == Band.PRIORITY || band == Band.NURTURE;
            case "priority" -> band == Band.PRIORITY;
            case "rejected", "disqualified" -> band == Band.DISQUALIFIED;
            case "nurture" -> band == Band.NURTURE;
            case "watch" -> band == Band.WATCH;
            case "thin" -> band == Band.THIN;
            default -> true;
        };
    }

    private static boolean matches(Company company, String needle) {
        return contains(company.getName(), needle)
                || contains(company.getDomain(), needle)
                || contains(company.getIndustry(), needle)
                || contains(company.getCountry(), needle)
                || contains(company.getHqCity(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Comparator<BoardRow> comparator(String sort) {
        String key = sort == null ? "score" : sort.toLowerCase(Locale.ROOT);
        Comparator<BoardRow> rank = switch (key) {
            case "name" -> Comparator.comparing(BoardRow::name, String.CASE_INSENSITIVE_ORDER);
            case "evidence" -> Comparator.comparingInt(BoardRow::evidenceCount).reversed();
            case "recent" -> Comparator.comparing(BoardRow::newestSignalAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "themes" -> Comparator.comparingInt(BoardRow::themeCount).reversed()
                    .thenComparing(Comparator.comparingDouble(BoardRow::probability).reversed());
            default -> Comparator.comparingDouble(BoardRow::probability).reversed()
                    .thenComparing(Comparator.comparingInt(BoardRow::evidenceCount).reversed())
                    .thenComparing(BoardRow::name, String.CASE_INSENSITIVE_ORDER);
        };
        return Comparator.comparingInt((BoardRow row) -> Band.DISQUALIFIED.name().equals(row.band()) ? 1 : 0)
                .thenComparing(rank);
    }

    private static BoardRow withRank(BoardRow row, int rank) {
        return new BoardRow(row.id(), rank, row.name(), row.domain(), row.country(), row.hqCity(), row.industry(),
                row.sizeBand(), row.competitor(), row.probability(), row.band(), row.bandLabel(), row.bandShort(),
                row.gateReason(),
                row.nats(), row.signalCount(), row.themeCount(), row.evidenceCount(), row.scannedDocuments(),
                row.silentDocuments(), row.newestSignalAt(), row.newestSignalAgeDays(), row.spark(), row.strongest(),
                row.chips(), row.themes(), row.profiles(), row.engine(), row.leadStatus(), row.leadStatusLabel(),
                row.lastContactedAt(),
                row.contactCount(), row.urgency(), row.whyNow());
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max - 1) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
