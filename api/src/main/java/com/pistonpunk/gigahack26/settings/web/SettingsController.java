package com.pistonpunk.gigahack26.settings.web;

import com.pistonpunk.gigahack26.classification.application.ClassifierProperties;
import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.scoring.application.ScoreboardService;
import com.pistonpunk.gigahack26.settings.application.SettingsRequest;
import com.pistonpunk.gigahack26.settings.application.SettingsService;
import com.pistonpunk.gigahack26.settings.domain.WorkspaceSettings;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final ScoreboardService scoreboard;
    private final CompanyRepository companies;
    private final ClassifierProperties classifier;

    public SettingsController(SettingsService settings, ScoreboardService scoreboard, CompanyRepository companies,
                              ClassifierProperties classifier) {
        this.settings = settings;
        this.scoreboard = scoreboard;
        this.companies = companies;
        this.classifier = classifier;
    }

    @GetMapping
    public SettingsView read() {
        return view(settings.current());
    }

    @PutMapping
    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    public SettingsView update(@Valid @RequestBody SettingsRequest request) {
        WorkspaceSettings saved = settings.update(request);
        scoreboard.rescoreAll();
        return view(saved);
    }

    private SettingsView view(WorkspaceSettings row) {
        List<Company> all = companies.findAll();
        List<String> countries = split(row.getIcpCountries());
        Map<String, Long> inScope = new LinkedHashMap<>();
        inScope.put("total", (long) all.size());
        inScope.put("inGeography", all.stream().filter(company -> inGeography(company, countries)).count());
        inScope.put("aboveSizeFloor", all.stream()
                .filter(company -> company.getEmployees() == null
                        || company.getEmployees() >= row.getIcpMinEmployees()).count());
        inScope.put("competitors", all.stream().filter(Company::isCompetitor).count());
        inScope.put("inIndustry", all.stream().filter(company -> inIndustry(company, row)).count());
        List<SettingsView.Exclusion> exclusions = all.stream()
                .filter(Company::isCompetitor)
                .sorted(java.util.Comparator.comparing(Company::getName))
                .map(company -> new SettingsView.Exclusion(company.getId().toString(), company.getName()))
                .toList();
        List<String> industriesPresent = all.stream()
                .map(Company::getIndustry)
                .filter(industry -> industry != null && !industry.isBlank())
                .distinct()
                .sorted()
                .toList();

        return new SettingsView(
                splitAsWritten(row.getBlacklist()),
                exclusions,
                split(row.getIcpCountries()),
                split(row.getIcpIndustries()),
                row.getIcpMinEmployees(),
                row.isGeographyGate(),
                row.isSizeGate(),
                row.isCompetitorGate(),
                row.isIndustryGate(),
                row.getConfidenceShrink(),
                row.getBaseRate(),
                row.getPriorityThreshold(),
                row.getNurtureThreshold(),
                row.getWatchThreshold(),
                row.getRankDiscount(),
                settings.effective().themeCap(),
                row.getVoiceTone(),
                row.getVoiceFormality(),
                row.getVoiceLength(),
                row.getVoiceLanguage(),
                row.getVoiceReader(),
                row.getVoiceAsk(),
                row.getVoiceSignOff(),
                row.getVoiceSignature(),
                row.getVoiceAvoid(),
                row.getVoiceNotes(),
                classifier.deepseek().configured(),
                row.getUpdatedAt(),
                inScope,
                industriesPresent,
                all.size());
    }

    private static boolean inIndustry(Company company, WorkspaceSettings row) {
        List<String> industries = split(row.getIcpIndustries());
        return industries.isEmpty() || company.getIndustry() == null
                || industries.contains(company.getIndustry().toUpperCase(Locale.ROOT));
    }

    private static boolean inGeography(Company company, List<String> countries) {
        return countries.isEmpty() || company.getCountry() == null
                || countries.contains(company.getCountry().toUpperCase(Locale.ROOT));
    }

    private static List<String> splitAsWritten(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.toUpperCase(Locale.ROOT).split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

}
