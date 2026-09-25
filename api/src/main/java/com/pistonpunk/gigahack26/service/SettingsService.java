package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.WorkspaceSettings;
import com.pistonpunk.gigahack26.repository.jpa.WorkspaceSettingsRepository;
import com.pistonpunk.gigahack26.web.dto.SettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SettingsService implements ScoringConfig {

    private final WorkspaceSettingsRepository settings;
    private final ScoringProperties defaults;
    private final Clock clock;

    public SettingsService(WorkspaceSettingsRepository settings, ScoringProperties defaults, Clock clock) {
        this.settings = settings;
        this.defaults = defaults;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceSettings current() {
        return settings.findById(WorkspaceSettings.SINGLETON).orElseGet(() -> settings.save(new WorkspaceSettings(
                WorkspaceSettings.SINGLETON,
                defaults.icpCountries(),
                "",
                defaults.icpMinEmployees(),
                true,
                true,
                true,
                defaults.baseRate(),
                defaults.priorityThreshold(),
                defaults.nurtureThreshold(),
                defaults.watchThreshold(),
                defaults.rankDiscount(),
                clock.instant())));
    }

    @Transactional
    public WorkspaceSettings update(SettingsRequest request) {
        WorkspaceSettings row = current();
        double priority = clamp(request.priorityThreshold(), 0.05, 0.99);
        double nurture = clamp(request.nurtureThreshold(), 0.02, priority - 0.01);
        double watch = clamp(request.watchThreshold(), 0.01, nurture - 0.005);
        row.revise(
                normalise(request.icpCountries()),
                normalise(request.icpIndustries()),
                Math.max(0, request.icpMinEmployees()),
                request.geographyGate(),
                request.sizeGate(),
                request.competitorGate(),
                clamp(request.baseRate(), 0.01, 0.9),
                priority,
                nurture,
                watch,
                clamp(request.rankDiscount(), 0.0, 1.0),
                clock.instant());
        return settings.save(row);
    }

    @Override
    public ScoringProperties effective() {
        WorkspaceSettings row = current();
        return new ScoringProperties(
                row.getBaseRate(),
                row.getPriorityThreshold(),
                row.getNurtureThreshold(),
                row.getWatchThreshold(),
                row.getRankDiscount(),
                row.getIcpMinEmployees(),
                row.getIcpCountries(),
                row.isGeographyGate(),
                row.isSizeGate(),
                row.isCompetitorGate(),
                row.getIcpIndustries());
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return String.join(",", value.toUpperCase().split("\\s*,\\s*")).replaceAll(",+", ",").replaceAll("^,|,$", "");
    }

    private static double clamp(double value, double low, double high) {
        if (Double.isNaN(value)) {
            return low;
        }
        return Math.max(low, Math.min(high, value));
    }
}
