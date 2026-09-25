package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.web.dto.OutreachDraft;

import java.util.List;

public interface DraftWriter {

    record Brief(
            String company,
            String serviceLine,
            String proposition,
            String persona,
            List<String> findings,
            Voice voice,
            String channel,
            String trigger) {
    }

    record Composed(String subject, String body, String engine) {
    }

    Composed write(Brief brief, List<OutreachDraft.Citation> citations);

    String id();
}
