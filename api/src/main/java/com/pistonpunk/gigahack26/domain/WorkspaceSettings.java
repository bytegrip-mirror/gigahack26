package com.pistonpunk.gigahack26.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {

    public static final UUID SINGLETON = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "icp_countries", nullable = false, length = 1000)
    private String icpCountries;

    @Column(name = "icp_industries", nullable = false, length = 1000)
    private String icpIndustries;

    @Column(name = "icp_min_employees", nullable = false)
    private int icpMinEmployees;

    @Column(name = "geography_gate", nullable = false)
    private boolean geographyGate;

    @Column(name = "size_gate", nullable = false)
    private boolean sizeGate;

    @Column(name = "competitor_gate", nullable = false)
    private boolean competitorGate;

    @Column(name = "base_rate", nullable = false)
    private double baseRate;

    @Column(name = "priority_threshold", nullable = false)
    private double priorityThreshold;

    @Column(name = "nurture_threshold", nullable = false)
    private double nurtureThreshold;

    @Column(name = "watch_threshold", nullable = false)
    private double watchThreshold;

    @Column(name = "rank_discount", nullable = false)
    private double rankDiscount;

    @Column(name = "voice_tone", length = 40)
    private String voiceTone;

    @Column(name = "voice_formality", length = 20)
    private String voiceFormality;

    @Column(name = "voice_length", length = 20)
    private String voiceLength;

    @Column(name = "voice_language", length = 10)
    private String voiceLanguage;

    @Column(name = "voice_reader", length = 80)
    private String voiceReader;

    @Column(name = "voice_ask", length = 200)
    private String voiceAsk;

    @Column(name = "voice_sign_off", length = 120)
    private String voiceSignOff;

    @Column(name = "voice_signature", length = 160)
    private String voiceSignature;

    @Column(name = "voice_avoid", length = 600)
    private String voiceAvoid;

    @Column(name = "voice_notes", length = 2000)
    private String voiceNotes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceSettings() {
    }

    public WorkspaceSettings(UUID id, String icpCountries, String icpIndustries, int icpMinEmployees,
                             boolean geographyGate, boolean sizeGate, boolean competitorGate, double baseRate,
                             double priorityThreshold, double nurtureThreshold, double watchThreshold,
                             double rankDiscount, Instant updatedAt) {
        this.id = id;
        this.icpCountries = icpCountries;
        this.icpIndustries = icpIndustries;
        this.icpMinEmployees = icpMinEmployees;
        this.geographyGate = geographyGate;
        this.sizeGate = sizeGate;
        this.competitorGate = competitorGate;
        this.baseRate = baseRate;
        this.priorityThreshold = priorityThreshold;
        this.nurtureThreshold = nurtureThreshold;
        this.watchThreshold = watchThreshold;
        this.rankDiscount = rankDiscount;
        this.voiceTone = "direct";
        this.voiceFormality = "neutral";
        this.voiceLength = "short";
        this.voiceLanguage = "en";
        this.voiceReader = "you";
        this.voiceAsk = "Would twenty minutes next week be useful?";
        this.voiceSignOff = "Best regards,";
        this.voiceSignature = "";
        this.voiceAvoid = "";
        this.voiceNotes = "";
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIcpCountries() {
        return icpCountries;
    }

    public String getIcpIndustries() {
        return icpIndustries;
    }

    public int getIcpMinEmployees() {
        return icpMinEmployees;
    }

    public boolean isGeographyGate() {
        return geographyGate;
    }

    public boolean isSizeGate() {
        return sizeGate;
    }

    public boolean isCompetitorGate() {
        return competitorGate;
    }

    public double getBaseRate() {
        return baseRate;
    }

    public double getPriorityThreshold() {
        return priorityThreshold;
    }

    public double getNurtureThreshold() {
        return nurtureThreshold;
    }

    public double getWatchThreshold() {
        return watchThreshold;
    }

    public double getRankDiscount() {
        return rankDiscount;
    }

    public String getVoiceTone() {
        return orDefault(voiceTone, "direct");
    }

    public String getVoiceFormality() {
        return orDefault(voiceFormality, "neutral");
    }

    public String getVoiceLength() {
        return orDefault(voiceLength, "short");
    }

    public String getVoiceLanguage() {
        return orDefault(voiceLanguage, "en");
    }

    public String getVoiceReader() {
        return orDefault(voiceReader, "you");
    }

    public String getVoiceAsk() {
        return orDefault(voiceAsk, "Would twenty minutes next week be useful?");
    }

    public String getVoiceSignOff() {
        return voiceSignOff == null ? "Best regards," : voiceSignOff;
    }

    public String getVoiceSignature() {
        return voiceSignature == null ? "" : voiceSignature;
    }

    public String getVoiceAvoid() {
        return voiceAvoid == null ? "" : voiceAvoid;
    }

    public String getVoiceNotes() {
        return voiceNotes == null ? "" : voiceNotes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void revise(String icpCountries, String icpIndustries, int icpMinEmployees, boolean geographyGate,
                       boolean sizeGate, boolean competitorGate, double baseRate, double priorityThreshold,
                       double nurtureThreshold, double watchThreshold, double rankDiscount, Instant now) {
        this.icpCountries = icpCountries;
        this.icpIndustries = icpIndustries;
        this.icpMinEmployees = icpMinEmployees;
        this.geographyGate = geographyGate;
        this.sizeGate = sizeGate;
        this.competitorGate = competitorGate;
        this.baseRate = baseRate;
        this.priorityThreshold = priorityThreshold;
        this.nurtureThreshold = nurtureThreshold;
        this.watchThreshold = watchThreshold;
        this.rankDiscount = rankDiscount;
        this.updatedAt = now;
    }

    public void reviseVoice(String tone, String formality, String length, String language, String reader,
                            String ask, String signOff, String signature, String avoid, String notes, Instant now) {
        this.voiceTone = orDefault(tone, "direct");
        this.voiceFormality = orDefault(formality, "neutral");
        this.voiceLength = orDefault(length, "short");
        this.voiceLanguage = orDefault(language, "en");
        this.voiceReader = orDefault(reader, "you");
        this.voiceAsk = orDefault(ask, "Would twenty minutes next week be useful?");
        this.voiceSignOff = signOff == null ? "" : signOff;
        this.voiceSignature = signature == null ? "" : signature;
        this.voiceAvoid = avoid == null ? "" : avoid;
        this.voiceNotes = notes == null ? "" : notes;
        this.updatedAt = now;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
