package com.pistonpunk.gigahack26.web.dto;

import java.util.List;
import java.util.UUID;

public record OutreachDraft(
        UUID companyId,
        String company,
        String channel,
        String subject,
        String body,
        List<Citation> citations,
        String persona,
        String valueProposition,
        String engine,
        UUID recipientId,
        String recipientName,
        String recipientEmail) {

    public record Citation(String marker, String question, String quote, String sourceUrl, String publisher) {
    }
}
