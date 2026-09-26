package com.pistonpunk.gigahack26.outreach.application;


import com.pistonpunk.gigahack26.outreach.domain.Voice;
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
            String trigger,
            String instruction) {

        boolean steered() {
            return instruction != null && !instruction.isBlank();
        }
    }

    record Composed(String subject, String body, String engine) {
    }

    Composed write(Brief brief, List<OutreachDraft.Citation> citations);

    String id();
}
