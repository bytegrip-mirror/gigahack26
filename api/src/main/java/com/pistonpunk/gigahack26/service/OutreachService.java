package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Person;
import com.pistonpunk.gigahack26.web.dto.CompanyDetail;
import com.pistonpunk.gigahack26.web.dto.OutreachDraft;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
            "we take one named use case to production and leave a platform your team can extend");

    private final PersonService people;
    private final BoardService board;
    private final SettingsService settings;
    private final ServiceLineService serviceLines;
    private final DraftWriterRouter writer;

    public OutreachService(PersonService people, BoardService board, SettingsService settings, ServiceLineService serviceLines,
                           DraftWriterRouter writer) {
        this.people = people;
        this.board = board;
        this.settings = settings;
        this.serviceLines = serviceLines;
        this.writer = writer;
    }

    public Optional<OutreachDraft> draft(UUID companyId, String serviceLine, String rawChannel, String instruction) {
        return board.detail(companyId, serviceLine).map(detail -> compose(detail, rawChannel, instruction));
    }

    OutreachDraft compose(CompanyDetail detail, String rawChannel, String instruction) {
        String channel = "inmail".equalsIgnoreCase(rawChannel) ? "inmail" : "email";
        Voice voice = Voice.of(settings.current());

        List<LedgerRow> evidence = detail.ledger().stream()
                .filter(row -> !row.blocking() && row.contributionNats() > 0 && row.spanText() != null)
                .sorted(Comparator.comparingDouble(LedgerRow::contributionNats).reversed())
                .limit(3)
                .toList();

        String proposition = serviceLines.list(false).stream()
                .filter(line -> line.key().equals(detail.serviceLine()))
                .map(line -> line.valueProposition())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> PROPOSITIONS.getOrDefault(detail.serviceLine(),
                        "we take the first step of this programme off your team's plate"));

        List<OutreachDraft.Citation> citations = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            LedgerRow row = evidence.get(i);
            citations.add(new OutreachDraft.Citation(
                    String.valueOf(i + 1),
                    row.questionText(),
                    collapse(row.spanText()),
                    row.sourceUrl(),
                    row.publisher()));
        }

        List<String> findings = evidence.stream().map(OutreachService::finding).toList();
        DraftWriter.Brief brief = new DraftWriter.Brief(
                detail.name(),
                detail.serviceLine(),
                proposition,
                PERSONAS.getOrDefault(detail.serviceLine(), DEFAULT_PERSONA),
                findings,
                voice,
                channel,
                detail.whyNow() == null ? null : detail.whyNow().headline(),
                instruction);

        DraftWriter.Composed composed = writer.write(brief, citations);

        // The draft is addressed to a person, not to a company: the first contact
        // whose job matches the seat this service line sells into, preferring one we
        // actually hold an address for.
        List<Person> contacts = people.contactsFor(detail.id());
        String wanted = brief.persona().toLowerCase();
        Person recipient = contacts.stream()
                .filter(person -> person.getEmail() != null)
                .filter(person -> wanted.contains(person.getRole().toLowerCase().split(" ")[0]))
                .findFirst()
                .or(() -> contacts.stream().filter(person -> person.getEmail() != null).findFirst())
                .or(() -> contacts.stream().findFirst())
                .orElse(null);

        return new OutreachDraft(
                detail.id(),
                detail.name(),
                channel,
                composed.subject(),
                composed.body(),
                citations,
                brief.persona(),
                proposition,
                composed.engine(),
                recipient == null ? null : recipient.getId(),
                recipient == null ? null : recipient.getName(),
                recipient == null ? null : recipient.getEmail());
    }

    static String finding(LedgerRow row) {
        String lowered = row.questionText().toLowerCase();
        String theme = row.theme() == null ? "the programme" : row.theme().replace('-', ' ');
        if (lowered.startsWith("is the company hiring")) {
            return "you are hiring for " + theme + " roles";
        }
        if (lowered.startsWith("has the company announced")) {
            return "you have announced " + article(theme) + " " + theme + " programme";
        }
        if (lowered.startsWith("has the company appointed")) {
            return "you have a new leader owning this";
        }
        if (lowered.startsWith("is the company subject")) {
            return "there is a dated obligation you are working to";
        }
        if (lowered.startsWith("has the company started")) {
            return "you have started a " + theme + " initiative";
        }
        if (lowered.startsWith("is the company investing")) {
            return "you are investing in " + theme;
        }
        return "there is public evidence around " + theme;
    }

    static String article(String word) {
        if (word == null || word.isBlank()) {
            return "a";
        }
        char first = Character.toLowerCase(word.charAt(0));
        return first == 'a' || first == 'e' || first == 'i' || first == 'o' || first == 'u' ? "an" : "a";
    }

    static String collapse(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
