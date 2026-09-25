package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.service.LedgerRow;

import java.util.List;

public record WhyNow(
        String headline,
        String narrative,
        String urgency,
        String urgencyLabel,
        List<LedgerRow> triggers,
        List<LedgerRow> counters,
        Double daysInBand) {
}
