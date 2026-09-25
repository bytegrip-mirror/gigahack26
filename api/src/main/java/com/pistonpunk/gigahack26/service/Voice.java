package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.WorkspaceSettings;

import java.util.Arrays;
import java.util.List;

public record Voice(
        String tone,
        String formality,
        String length,
        String language,
        String reader,
        String ask,
        String signOff,
        String signature,
        List<String> avoid,
        String notes) {

    public static Voice of(WorkspaceSettings settings) {
        return new Voice(
                settings.getVoiceTone(),
                settings.getVoiceFormality(),
                settings.getVoiceLength(),
                settings.getVoiceLanguage(),
                settings.getVoiceReader(),
                settings.getVoiceAsk(),
                settings.getVoiceSignOff(),
                settings.getVoiceSignature(),
                split(settings.getVoiceAvoid()),
                settings.getVoiceNotes());
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("Tone: ").append(tone).append(". ");
        text.append("Register: ").append(formality).append(". ");
        text.append("Length: ").append(length).append(". ");
        text.append("Write in ").append(language).append(". ");
        text.append("Address the reader as \"").append(reader).append("\". ");
        if (!avoid.isEmpty()) {
            text.append("Never use these words or phrases: ").append(String.join(", ", avoid)).append(". ");
        }
        if (notes != null && !notes.isBlank()) {
            text.append(notes);
        }
        return text.toString().trim();
    }

    public int targetWords() {
        return switch (length == null ? "short" : length.toLowerCase()) {
            case "long" -> 220;
            case "medium" -> 150;
            default -> 95;
        };
    }
}
