package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Band;
import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.CompanyAlias;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Lead;
import com.pistonpunk.gigahack26.domain.PolicyScore;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyAliasRepository;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyScoreRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.web.dto.BoardRow;
import com.pistonpunk.gigahack26.web.dto.CompanyDetail;
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
    private final ClassifierRouter router;
    private final ScoringConfig settings;
    private final RegionService regions;
    private final Clock clock;

    public BoardService(CompanyRepository companies, CompanyAliasRepository aliases, EvidenceRepository evidence,
                        SignalRepository signals, PolicyScoreRepository scores, ScoreboardService scoreboard,
                        ScoringService scoringService, WhyNowService whyNow, LeadService leads,
                        ClassifierRouter router, ScoringConfig settings, RegionService regions, Clock clock) {
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
        this.settings = settings;
        this.regions = regions;
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

        Map<UUID, Company> companiesById = companies.findAll().stream()
                .collect(Collectors.toMap(Company::getId, company -> company));
        Map<UUID, List<Evidence>> evidenceByCompany = evidence.findAll().stream()
                .collect(Collectors.groupingBy(Evidence::getCompanyId));
        Map<UUID, List<Signal>> signalsByCompany = signals.findAll().stream()
                .collect(Collectors.groupingBy(Signal::getCompanyId));
        Map<UUID, Lead> leadsByCompany = leads.byCompany(serviceLine);
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
                    leadsByCompany.get(company.getId()), now));
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
        List<com.pistonpunk.gigahack26.domain.PolicyQuestion> active =
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
                detail.weakestLink(),
                detail.priorityHorizonDays(),
                detail.nurtureHorizonDays(),
                detail.sensitivity(),
                evidenceViews,
                now,
                router.primaryEngineId(),
                whyNow.explain(detail, settings.effective()),
                leads.find(companyId, serviceLine).map(lead -> new com.pistonpunk.gigahack26.web.dto.LeadView(
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

    private BoardRow toRow(Company company, ScoreComputation detail, List<Evidence> companyEvidence,
                           List<Signal> companySignals, Lead lead, Instant now) {
        List<LedgerRow> ranked = detail.rows().stream()
                .filter(row -> !row.blocking())
                .sorted(Comparator.comparingDouble((LedgerRow row) -> Math.abs(row.contributionNats())).reversed())
                .toList();

        List<BoardRow.Chip> chips = ranked.stream().limit(6).map(BoardService::toChip).toList();
        BoardRow.Chip strongest = chips.isEmpty() ? null : chips.get(0);
        WhyNowService.Brief why = whyNow.brief(detail, settings.effective());

        Instant newest = detail.rows().stream()
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
                strongest,
                chips,
                new ArrayList<>(detail.themeTotals().keySet()),
                lead == null ? null : lead.getStatus().name(),
                lead == null ? null : lead.getStatus().label(),
                lead == null ? null : lead.getLastContactedAt(),
                lead == null ? 0 : lead.getContactCount(),
                why.urgency(),
                why.headline());
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
                row.silentDocuments(), row.newestSignalAt(), row.newestSignalAgeDays(), row.strongest(),
                row.chips(), row.themes(), row.leadStatus(), row.leadStatusLabel(), row.lastContactedAt(),
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
