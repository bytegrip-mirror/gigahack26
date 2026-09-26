package com.pistonpunk.gigahack26.settings.application;

import com.pistonpunk.gigahack26.scoring.application.ScoringConfig;
import com.pistonpunk.gigahack26.scoring.application.ScoringProperties;
import com.pistonpunk.gigahack26.settings.domain.WorkspaceSettings;
import com.pistonpunk.gigahack26.settings.infrastructure.WorkspaceSettingsRepository;
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
                defaults.icpIndustries(),
                defaults.icpMinEmployees(),
                true,
                true,
                true,
                defaults.industryGate(),
                defaults.confidenceShrink(),
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
                Boolean.TRUE.equals(request.industryGate()),
                clampShrink(request.confidenceShrink()),
                clamp(request.baseRate(), 0.01, 0.9),
                priority,
                nurture,
                watch,
                clamp(request.rankDiscount(), 0.0, 1.0),
                clock.instant());
        row.reviseBlacklist(request.blacklist(), clock.instant());
        row.reviseVoice(request.voiceTone(), request.voiceFormality(), request.voiceLength(),
                request.voiceLanguage(), request.voiceReader(), request.voiceAsk(), request.voiceSignOff(),
                request.voiceSignature(), request.voiceAvoid(), request.voiceNotes(), clock.instant());
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
                row.isIndustryGate(),
                row.getConfidenceShrink(),
                row.getIcpIndustries(),
                row.getBlacklist());
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return String.join(",", value.toUpperCase().split("\\s*,\\s*")).replaceAll(",+", ",").replaceAll("^,|,$", "");
    }

    private static Double clampShrink(Double value) {
        if (value == null || Double.isNaN(value)) {
            return null;
        }
        return Math.max(0.1, Math.min(1.0, value));
    }

    private static double clamp(double value, double low, double high) {
        if (Double.isNaN(value)) {
            return low;
        }
        return Math.max(low, Math.min(high, value));
    }
}
