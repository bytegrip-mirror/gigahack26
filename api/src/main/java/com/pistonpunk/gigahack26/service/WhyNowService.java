package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.web.dto.WhyNow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhyNowService {

    private static final int TRIGGERS = 3;
    private static final int COUNTERS = 3;

    public WhyNow explain(ScoreComputation detail, ScoringProperties properties) {
        List<LedgerRow> positives = detail.rows().stream()
                .filter(row -> !row.blocking() && row.contributionNats() > 0)
                .sorted((a, b) -> Double.compare(b.contributionNats(), a.contributionNats()))
                .toList();

        List<LedgerRow> counters = detail.counterEvidence().stream()
                .sorted((a, b) -> Double.compare(a.contributionNats(), b.contributionNats()))
                .toList();

        if (positives.isEmpty()) {
            return new WhyNow(
                    detail.band() == com.pistonpunk.gigahack26.domain.Band.DISQUALIFIED
                            ? "Out of the queue — " + detail.gateReason()
                            : "No dated reason to make contact",
                    detail.band() == com.pistonpunk.gigahack26.domain.Band.DISQUALIFIED
                            ? "The evidence still scores, but a gate removed the company. Nothing here is worth a call."
                            : "No document in the corpus answers any active question for this company at a usable "
                                    + "confidence. That is a result, not a gap: there is nothing to open a conversation with.",
                    "NONE",
                    "No trigger",
                    List.of(),
                    counters.stream().limit(COUNTERS).toList(),
                    null);
        }

        LedgerRow trigger = positives.get(0);
        String urgency = urgency(detail, trigger, properties);

        return new WhyNow(
                headline(trigger),
                narrative(detail, positives, counters, trigger, properties),
                urgency,
                urgencyLabel(urgency),
                positives.stream().limit(TRIGGERS).toList(),
                counters.stream().limit(COUNTERS).toList(),
                detail.priorityHorizonDays());
    }

    private String headline(LedgerRow trigger) {
        return "%s — %s, %s".formatted(shorten(trigger.questionText()), source(trigger.publisher()),
                age(trigger.ageDays()));
    }

    static String source(String publisher) {
        String host = publisher == null ? "" : publisher.toLowerCase();
        for (String prefix : new String[]{"jobs.", "news.", "press.", "www.", "careers.", "ir."}) {
            if (host.startsWith(prefix)) {
                host = host.substring(prefix.length());
                break;
            }
        }
        return host.isBlank() ? "an unnamed source" : host;
    }

    private String narrative(ScoreComputation detail, List<LedgerRow> positives, List<LedgerRow> counters,
                             LedgerRow trigger, ScoringProperties properties) {
        StringBuilder text = new StringBuilder();

        text.append("It is the strongest of ")
                .append(positives.size())
                .append(positives.size() == 1 ? " weighted signal" : " weighted signals")
                .append(" pointing at ").append(trigger.theme()).append(".");
        if (trigger.ageDays() < 45) {
            text.append(" It is recent enough to open with.");
        } else {
            text.append(" It is ").append(age(trigger.ageDays()))
                    .append(", so lead with the question rather than the claim.");
        }

        int themes = detail.themeCount();
        if (themes >= 2) {
            text.append(" ").append(themes)
                    .append(" independent kinds of evidence agree, which is what put this company in the top band:");
            text.append(" ").append(String.join(", ", detail.themeTotals().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> entry.getKey() + " " + String.format("%.2f", entry.getValue()))
                    .toList())).append(" nats.");
        } else {
            text.append(" Only one kind of evidence is present, so it reaches the ")
                    .append(String.format("%.2f", properties.priorityThreshold()))
                    .append(" line and stops there — a second independent theme is what would move it up.");
        }

        if (counters.isEmpty()) {
            text.append(" Nothing in the corpus argues against contacting them.");
        } else {
            text.append(" ").append(counters.size())
                    .append(counters.size() == 1 ? " item argues" : " items argue")
                    .append(" against, worth ")
                    .append(String.format("%.2f", counters.stream().mapToDouble(LedgerRow::contributionNats).sum()))
                    .append(" nats — read it before you write.");
        }

        Double horizon = detail.priorityHorizonDays();
        if (horizon != null && horizon > 0) {
            text.append(" Without new evidence this stays in the top band for about ")
                    .append(Math.round(horizon)).append(" more days.");
        } else if (detail.band() == com.pistonpunk.gigahack26.domain.Band.PRIORITY) {
            text.append(" The evidence is fresh enough that the band is not time-limited yet.");
        } else {
            text.append(" It is below the top band and time alone will not lift it.");
        }

        return text.toString();
    }

    private String urgency(ScoreComputation detail, LedgerRow trigger, ScoringProperties properties) {
        if (detail.band() == com.pistonpunk.gigahack26.domain.Band.DISQUALIFIED) {
            return "NONE";
        }
        boolean strong = trigger.contributionNats() >= 0.5;
        boolean fresh = trigger.ageDays() <= 45;
        boolean corroborated = detail.themeCount() >= 2;

        if (strong && fresh && corroborated) {
            return "ACT_NOW";
        }
        if (strong && (fresh || corroborated)) {
            return "THIS_WEEK";
        }
        return "MONITOR";
    }

    private String urgencyLabel(String urgency) {
        return switch (urgency) {
            case "ACT_NOW" -> "Act now";
            case "THIS_WEEK" -> "This week";
            case "MONITOR" -> "Monitor";
            default -> "No trigger";
        };
    }

    private static String age(double days) {
        if (days < 1) {
            return "published today";
        }
        if (days < 2) {
            return "published yesterday";
        }
        if (days < 45) {
            return "published " + Math.round(days) + " days ago";
        }
        if (days < 365) {
            return "published " + Math.round(days / 30) + " months ago";
        }
        return "published over a year ago";
    }

    private static String shorten(String question) {
        String text = question.trim();
        String lowered = text.toLowerCase();
        if (lowered.startsWith("is the company hiring")) {
            return "Hiring for the capability";
        }
        if (lowered.startsWith("has the company announced")) {
            return "An announced programme";
        }
        if (lowered.startsWith("has the company appointed")) {
            return "A new appointment";
        }
        if (lowered.startsWith("is the company subject")) {
            return "A compliance obligation";
        }
        if (lowered.startsWith("has the company suffered")) {
            return "A reported incident";
        }
        if (lowered.startsWith("has the company started")) {
            return "A started initiative";
        }
        if (lowered.startsWith("is the company building")) {
            return "They are building it themselves";
        }
        if (lowered.startsWith("does the company")) {
            return "An existing vendor relationship";
        }
        return text.length() <= 60 ? text : text.substring(0, 57).trim() + "…";
    }
}
