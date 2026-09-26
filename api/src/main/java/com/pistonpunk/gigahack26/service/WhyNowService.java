package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.web.dto.WhyNow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhyNowService {

    private static final int TRIGGERS = 3;
    private static final int COUNTERS = 3;

    public record Brief(String headline, String urgency) {
    }

    public Brief brief(ScoreComputation detail, ScoringProperties properties) {
        List<LedgerRow> positives = positives(detail);
        if (positives.isEmpty()) {
            return new Brief(
                    detail.band() == com.pistonpunk.gigahack26.domain.Band.DISQUALIFIED
                            ? "Out of the queue — " + detail.gateReason()
                            : "No dated reason to make contact",
                    "NONE");
        }
        LedgerRow trigger = positives.get(0);
        return new Brief(headline(trigger), urgency(detail, trigger, properties));
    }

    private static List<LedgerRow> positives(ScoreComputation detail) {
        return detail.rows().stream()
                .filter(row -> !row.blocking() && row.contributionNats() > 0)
                .sorted((a, b) -> Double.compare(b.contributionNats(), a.contributionNats()))
                .toList();
    }

    public WhyNow explain(ScoreComputation detail, ScoringProperties properties) {
        List<LedgerRow> positives = positives(detail);

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
                age(trigger));
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

    /**
     * Why now, in the words a rep would use.
     *
     * This used to explain the scoring model — the count of weighted signals,
     * the nats each theme contributed, the line a single theme stops at. Every
     * one of those numbers is real and every one of them is on the ledger, one
     * tab across, for the reader who wants to check the arithmetic. What a
     * person opening a lead wants first is what happened, when, and whether
     * anything argues against calling. So the narrative names the events, dates
     * them, and names the disagreement; the arithmetic stays where it belongs.
     */
    private String narrative(ScoreComputation detail, List<LedgerRow> positives, List<LedgerRow> counters,
                             LedgerRow trigger, ScoringProperties properties) {
        StringBuilder text = new StringBuilder();
        text.append(happened(trigger)).append(" — ").append(source(trigger.publisher()))
                .append(", ").append(age(trigger)).append(".");

        List<LedgerRow> others = positives.stream()
                .filter(row -> !row.theme().equals(trigger.theme()))
                .limit(2)
                .toList();
        if (!others.isEmpty()) {
            text.append(" Separately, ").append(joinPlain(others)).append(".");
            text.append(" Two unrelated things pointing the same way is what separates this from a company that "
                    + "merely mentioned the subject once.");
        } else if (positives.size() > 1) {
            text.append(" ").append(positives.size() - 1)
                    .append(positives.size() == 2 ? " further document says" : " further documents say")
                    .append(" the same kind of thing, so the reading does not rest on one page.");
        } else {
            text.append(" Nothing else in the corpus says anything of the same kind, so this rests on one document.");
        }

        if (!counters.isEmpty()) {
            text.append(" Against it: ").append(happened(counters.get(0))).append(", ")
                    .append(age(counters.get(0)))
                    .append(". Read that before writing.");
        }

        Double horizon = detail.priorityHorizonDays();
        if (detail.band() == com.pistonpunk.gigahack26.domain.Band.PRIORITY
                || detail.band() == com.pistonpunk.gigahack26.domain.Band.NURTURE) {
            text.append(horizon != null && horizon > 0
                    ? " On the evidence here it holds for about " + Math.round(horizon) + " more days."
                    : " The evidence is fresh, so the opening is now rather than later.");
        }
        return text.toString();
    }

    /**
     * The question title as an event rather than as a question.
     *
     * The titles are written as questions because they are what the classifier
     * answers; on a card they read better as the thing that happened.
     */
    static String happened(LedgerRow row) {
        String title = row.questionTitle() == null || row.questionTitle().isBlank()
                ? shorten(row.questionText())
                : row.questionTitle();
        String lowered = title.toLowerCase();
        if (lowered.startsWith("an announced") || lowered.startsWith("a named") || lowered.startsWith("a ")) {
            return Character.toUpperCase(title.charAt(0)) + title.substring(1);
        }
        return title;
    }

    private static String joinPlain(List<LedgerRow> rows) {
        List<String> parts = rows.stream()
                .map(row -> happened(row) + " (" + age(row) + ")")
                .toList();
        return String.join(", and ", parts);
    }

    /**
     * When the evidence is from — published if the page said, captured if not.
     *
     * A newsroom page with no date on it is anchored to the day the crawler read
     * it, and calling that "published today" claims the company said something
     * this morning when all that is known is that we looked. The ledger already
     * records which of the two it is; the card says which.
     */
    static String age(LedgerRow row) {
        String verb = "observed".equals(row.anchorKind()) ? "captured" : "published";
        return verb + " " + span(row.ageDays());
    }

    private static String span(double days) {
        if (days < 1) {
            return "today";
        }
        if (days < 2) {
            return "yesterday";
        }
        if (days < 45) {
            return Math.round(days) + " days ago";
        }
        if (days < 365) {
            return Math.round(days / 30) + " months ago";
        }
        return "over a year ago";
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
