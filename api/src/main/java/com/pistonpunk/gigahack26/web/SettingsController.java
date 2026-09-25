package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.WorkspaceSettings;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.service.ScoreboardService;
import com.pistonpunk.gigahack26.service.SettingsService;
import com.pistonpunk.gigahack26.web.dto.SettingsRequest;
import com.pistonpunk.gigahack26.web.dto.SettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public SettingsController(SettingsService settings, ScoreboardService scoreboard, CompanyRepository companies) {
        this.settings = settings;
        this.scoreboard = scoreboard;
        this.companies = companies;
    }

    @GetMapping
    public SettingsView read() {
        return view(settings.current());
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsRequest request,
                               @RequestParam(name = "serviceLine", required = false) String serviceLine) {
        WorkspaceSettings saved = settings.update(request);
        scoreboard.rescore(line(serviceLine));
        return view(saved);
    }

    private SettingsView view(WorkspaceSettings row) {
        List<Company> all = companies.findAll();
        Map<String, Long> inScope = new LinkedHashMap<>();
        inScope.put("total", (long) all.size());
        inScope.put("inGeography", all.stream().filter(this::inGeography).count());
        inScope.put("aboveSizeFloor", all.stream()
                .filter(company -> company.getEmployees() == null
                        || company.getEmployees() >= row.getIcpMinEmployees()).count());
        inScope.put("competitors", all.stream().filter(Company::isCompetitor).count());

        return new SettingsView(
                split(row.getIcpCountries()),
                split(row.getIcpIndustries()),
                row.getIcpMinEmployees(),
                row.isGeographyGate(),
                row.isSizeGate(),
                row.isCompetitorGate(),
                row.getBaseRate(),
                row.getPriorityThreshold(),
                row.getNurtureThreshold(),
                row.getWatchThreshold(),
                row.getRankDiscount(),
                settings.effective().themeCap(),
                row.getUpdatedAt(),
                inScope,
                all.size());
    }

    private boolean inGeography(Company company) {
        List<String> countries = split(settings.current().getIcpCountries());
        return countries.isEmpty() || company.getCountry() == null
                || countries.contains(company.getCountry().toUpperCase(Locale.ROOT));
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

    private static String line(String serviceLine) {
        return serviceLine == null || serviceLine.isBlank() ? PolicyLibrary.AUTOMATION : serviceLine;
    }
}
