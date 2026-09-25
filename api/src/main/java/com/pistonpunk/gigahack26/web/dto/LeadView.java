package com.pistonpunk.gigahack26.web.dto;

import java.time.Instant;
import java.util.UUID;

public record LeadView(
        UUID id,
        UUID companyId,
        String company,
        String domain,
        String serviceLine,
        String status,
        String statusLabel,
        String channel,
        Instant lastContactedAt,
        int contactCount,
        String note,
        String outreachSubject,
        Instant crmSyncedAt,
        Instant updatedAt) {
}
