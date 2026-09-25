package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.web.dto.CompanyDetail;
import com.pistonpunk.gigahack26.web.dto.OutreachDraft;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OutreachService {

    private static final Map<String, String> PERSONAS = Map.of(
            PolicyLibrary.AUTOMATION, "COO or Head of Process Excellence",
            PolicyLibrary.SECURITY, "CISO or Head of Information Security",
            PolicyLibrary.DATA, "Chief Data Officer or Head of Data Platform");

    private static final String DEFAULT_PERSONA = "operations leader who owns this question";

    private static final Map<String, String> PROPOSITIONS = Map.of(
            PolicyLibrary.AUTOMATION,
            "we run the automation programme end to end so your team keeps the process knowledge and we carry the delivery",
            PolicyLibrary.SECURITY,
            "we close the compliance gap against a dated obligation and hand over a running capability, not a report",
            PolicyLibrary.DATA,
            "we take one named use case to production and leave the platform your team can extend");

    private final BoardService board;
    private final ClassifierRouter router;

    public OutreachService(BoardService board, ClassifierRouter router) {
        this.board = board;
        this.router = router;
    }

    public Optional<OutreachDraft> draft(java.util.UUID companyId, String serviceLine, String channel) {
        return board.detail(companyId, serviceLine).map(detail -> compose(detail, channel));
    }

    private OutreachDraft compose(CompanyDetail detail, String rawChannel) {
        String channel = "inmail".equalsIgnoreCase(rawChannel) ? "inmail" : "email";
        List<LedgerRow> evidence = detail.ledger().stream()
                .filter(row -> !row.blocking() && row.contributionNats() > 0 && row.spanText() != null)
                .sorted(Comparator.comparingDouble(LedgerRow::contributionNats).reversed())
                .limit(3)
                .toList();

        String persona = PERSONAS.getOrDefault(detail.serviceLine(), DEFAULT_PERSONA);
        String proposition = PROPOSITIONS.getOrDefault(detail.serviceLine(),
                "we take the first step of this programme off your team's plate");

        List<OutreachDraft.Citation> citations = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            LedgerRow row = evidence.get(i);
            citations.add(new OutreachDraft.Citation(
                    "[" + (i + 1) + "]",
                    row.questionText(),
                    collapse(row.spanText()),
                    row.sourceUrl(),
                    row.publisher()));
        }

        String body = channel.equals("inmail")
                ? inmail(detail, evidence, citations, proposition)
                : email(detail, evidence, citations, proposition);

        return new OutreachDraft(
                detail.id(),
                detail.name(),
                channel,
                subject(detail, evidence),
                body,
                citations,
                persona,
                proposition,
                router.primaryEngineId());
    }

    private String subject(CompanyDetail detail, List<LedgerRow> evidence) {
        if (evidence.isEmpty()) {
            return detail.name() + " — a question about your current programme";
        }
        LedgerRow strongest = evidence.get(0);
        return "%s — %s".formatted(detail.name(), headlineFor(strongest));
    }

    private String email(CompanyDetail detail, List<LedgerRow> evidence,
                         List<OutreachDraft.Citation> citations, String proposition) {
        StringBuilder body = new StringBuilder();
        body.append("Hello,\n\n");
        body.append(opening(detail, evidence, citations));
        body.append("\n\n");
        body.append("We work with teams in exactly that position: ").append(proposition).append(".\n\n");
        body.append("If it is useful, I can walk you through how a comparable programme was scoped in three weeks "
                + "and what it would take to do the same at ").append(detail.name()).append(".\n\n");
        body.append("Would a 20 minute call next week work?\n\n");
        body.append("Best regards,");
        return body.toString();
    }

    private String inmail(CompanyDetail detail, List<LedgerRow> evidence,
                          List<OutreachDraft.Citation> citations, String proposition) {
        StringBuilder body = new StringBuilder();
        body.append(opening(detail, evidence, citations));
        body.append("\n\n");
        body.append("We help with exactly this: ").append(proposition).append(".");
        body.append("\n\n");
        body.append("Worth a short conversation?");
        return body.toString();
    }

    private String opening(CompanyDetail detail, List<LedgerRow> evidence,
                           List<OutreachDraft.Citation> citations) {
        if (citations.isEmpty()) {
            return "I have been following " + detail.name()
                    + " and I have a question about how your team is approaching "
                    + detail.serviceLine().toLowerCase() + " this year.";
        }

        StringBuilder opening = new StringBuilder("I noticed ");
        for (int i = 0; i < citations.size(); i++) {
            if (i > 0) {
                opening.append(i == citations.size() - 1 ? " and " : ", ");
            }
            opening.append(describe(evidence.get(i), citations.get(i)));
        }
        opening.append(".");
        return opening.toString();
    }

    private String describe(LedgerRow row, OutreachDraft.Citation citation) {
        return "%s %s, %s".formatted(shortQuestion(row), citation.marker(), age(row));
    }

    private String shortQuestion(LedgerRow row) {
        String text = row.questionText();
        String lowered = text.toLowerCase();
        if (lowered.startsWith("is the company hiring")) {
            return "that you are hiring for " + row.theme() + " roles";
        }
        if (lowered.startsWith("has the company announced")) {
            return "the " + row.theme() + " programme you have announced";
        }
        if (lowered.startsWith("has the company appointed")) {
            return "the recent security leadership change";
        }
        if (lowered.startsWith("is the company subject")) {
            return "the compliance obligation you are working to";
        }
        if (lowered.startsWith("has the company suffered")) {
            return "the security incident that was reported";
        }
        if (lowered.startsWith("has the company started")) {
            return "the " + row.theme() + " initiative you have started";
        }
        return "your published " + row.theme() + " activity";
    }

    private String headlineFor(LedgerRow row) {
        return "a note on your " + row.theme().replace('-', ' ').toLowerCase() + " signals";
    }

    private static String age(LedgerRow row) {
        double days = row.ageDays();
        if (days < 1) {
            return "published today";
        }
        if (days < 30) {
            return "published " + (int) days + " days ago";
        }
        return "published " + (int) (days / 30) + " months ago";
    }

    private static String collapse(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    static double ageDays(Instant anchor, Instant now) {
        return Math.max(0, Duration.between(anchor, now).toHours() / 24.0);
    }
}
