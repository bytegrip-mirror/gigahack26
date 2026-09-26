package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.Person;
import com.pistonpunk.gigahack26.domain.PersonSignal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PersonRepository;
import com.pistonpunk.gigahack26.repository.jpa.PersonSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Places the people the demo opens with, and what they have said.
 *
 * People are otherwise only ever found by reading the corpus on request, which
 * leaves the screen empty until somebody presses the button. These arrive with
 * the deployment instead. Twelve are written out in `corpus/people.json`; the
 * rest are invented here from fixed pools of names, roles and things people say,
 * seeded so a restart produces the same hundred rather than a different hundred.
 *
 * Every one of them is marked in the database, so nothing here reads as though it
 * had been extracted from a document. Runs after {@link CorpusSeeder}, whose
 * companies they attach to.
 */
@Component
@Order(20)
public class PersonSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersonSeeder.class);
    private static final String ORIGIN = "demo-seed";
    private static final String MENTION = "corpus-mention";

    /** Enough to fill the People screen properly without burying the board in them. */
    private static final int TARGET = 100;

    /** Fixed, so the same hundred people come back after every restart. */
    private static final long SEED = 20260927L;

    private static final List<String> GIVEN = List.of(
            "Anna", "Marek", "Sofia", "Jonas", "Élodie", "Pieter", "Femke", "Luca", "Katrin", "Thomas",
            "Bram", "Clara", "Matteo", "Ingrid", "Tomas", "Petra", "Lars", "Sara", "Niels", "Elena",
            "Jan", "Marta", "Oliver", "Nina", "Pavel", "Ines", "Rafael", "Astrid", "Milan", "Greta",
            "Hugo", "Iris", "Kaspar", "Lena", "Morten", "Nora", "Otto", "Paula", "Quentin", "Rita",
            "Stefan", "Tessa", "Ulf", "Vera", "Wouter", "Xenia", "Yannick", "Zofia", "Andrei", "Beatriz",
            "Csaba", "Dorota", "Emil", "Franziska", "Goran", "Helena", "Ivan", "Jelena", "Karel", "Lucia",
            "Mihai", "Nadia", "Ondrej", "Pia", "Rasmus", "Simona", "Tibor", "Viktor", "Wanda", "Aleksandra");

    private static final List<String> FAMILY = List.of(
            "Novak", "Vogel", "Dvořák", "Lindqvist", "de Vries", "Weiß", "Marchand", "Bakker", "Jansen",
            "Bernasconi", "Kowalczyk", "Reuter", "Horvat", "Nagy", "Andersen", "Kovács", "Petrov", "Nowak",
            "Berg", "Silva", "Meyer", "Rossi", "Dubois", "Peeters", "Larsen", "Kaczmarek", "Sørensen",
            "Fischer", "Moreau", "Bianchi", "Janssen", "Nielsen", "Wagner", "Ólafsson", "Zieliński", "Popa",
            "Ivanov", "Tóth", "Hansen", "Weber", "Fontaine", "García", "Keller", "Costa", "Laurent",
            "Vermeulen", "Eriksen", "Mazurek", "Svensson", "Dimitrov", "Ahonen", "Rusu", "Bartoš",
            "Molnár", "Lehmann", "Caruso", "Duarte", "Kristensen", "Schmidt", "Varga", "Popescu");

    private static final List<String> ROLES = List.of(
            "Chief Technology Officer", "Chief Information Officer", "Chief Operating Officer",
            "Chief Digital Officer", "Chief Information Security Officer", "Head of Intelligent Automation",
            "Head of Digital Transformation", "Head of Data & AI", "Head of Global Business Services",
            "Head of Process Excellence", "Head of Enterprise Architecture", "Director of IT Operations",
            "Director of Shared Services", "VP Engineering", "Head of Automation CoE",
            "Head of Customer Operations");

    /** Nothing here claims a document said it: these are written lines with a
     * plausible shape, and the row is marked so nobody reads them as quotes. */
    private static final List<Template> TEMPLATES = List.of(
            new Template("BLOG", "STRONG", "Personal blog",
                    "Wrote a blog post arguing that %s back-office work should be automated end to end."),
            new Template("BLOG", "MEDIUM", "Company blog",
                    "Wrote about consolidating shared service centres across the group."),
            new Template("BLOG", "MEDIUM", "Personal blog",
                    "Wrote that the current tooling has plateaued and the next step is process intelligence."),
            new Template("BLOG", "MEDIUM", "Company blog",
                    "Argued that the finance close should be automated end to end."),
            new Template("X_POST", "MEDIUM", "X",
                    "Posted that the team is hiring process-mining engineers."),
            new Template("X_POST", "MEDIUM", "X",
                    "Posted that the next two quarters are about consolidating platforms."),
            new Template("X_POST", "SMALL", "X",
                    "Shared a photograph from a site visit with no commentary on technology."),
            new Template("LINKEDIN_POST", "STRONG", "LinkedIn",
                    "Announced a new automation centre of excellence and said the team will double within the year."),
            new Template("LINKEDIN_POST", "MEDIUM", "LinkedIn",
                    "Posted that the group is standardising its operations platforms across markets."),
            new Template("LINKEDIN_POST", "SMALL", "LinkedIn",
                    "Congratulated a colleague on a certification."),
            new Template("PRESS", "STRONG", "Trade press",
                    "Told a trade paper that cost reduction is now a board-level target with a quantified headcount aim."),
            new Template("PRESS", "MEDIUM", "Trade press",
                    "Named in %a trade article about a new operations programme."),
            new Template("PRESS", "MEDIUM", "Business press",
                    "Said in an interview that the shared service centres will absorb more work before any new ones open."),
            new Template("CONFERENCE", "STRONG", "Industry conference",
                    "Gave a keynote on what the next wave of automation actually delivers, and what it has not."),
            new Template("CONFERENCE", "MEDIUM", "Industry conference",
                    "Spoke at %a operations summit about the limits of the current tooling."),
            new Template("PODCAST", "SMALL", "Industry podcast",
                    "Appeared on an industry podcast to discuss automation returns."),
            new Template("NEWS", "MEDIUM", "Trade press",
                    "Quoted in %a report on the group's digital programme."),
            new Template("HIRING_POST", "MEDIUM", "Careers site",
                    "Posted an opening for a Head of Intelligent Automation reporting to them."));

    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final PersonRepository people;
    private final PersonSignalRepository signals;
    private final CorpusProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PersonSeeder(CompanyRepository companies, EvidenceRepository evidence, PersonRepository people,
                        PersonSignalRepository signals, CorpusProperties properties, ObjectMapper mapper,
                        Clock clock) {
        this.companies = companies;
        this.evidence = evidence;
        this.people = people;
        this.signals = signals;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    private record Template(String kind, String strength, String publisher, String text) {
    }

    private record Said(String kind, String strength, String text, String publisher, Instant publishedAt) {
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /**
     * Idempotent on the same key the extractor dedupes on — a company and a
     * folded name — so a restart, or a press of "Read for names", cannot double
     * a person up.
     */
    @Transactional
    public int seed() {
        Map<UUID, Set<String>> held = new LinkedHashMap<>();
        for (Person person : people.findAll()) {
            held.computeIfAbsent(person.getCompanyId(), key -> new HashSet<>())
                    .add(person.getName().toLowerCase(Locale.ROOT));
        }
        Map<UUID, UUID> anchors = new LinkedHashMap<>();
        Instant now = clock.instant();

        int added = fromFile(held, anchors, now);
        long total = people.count();
        if (total < TARGET) {
            added += invent((int) (TARGET - total), held, anchors, now);
        }
        if (added > 0) {
            log.info("placed {} people with their signals", added);
        }
        int filled = backfill(now);
        if (filled > 0) {
            log.info("gave {} people with nothing said a signal", filled);
        }
        return added;
    }

    /**
     * People read out of the corpus arrive with a name and a role and nothing
     * they have said, so the People screen showed a signal on a dozen of a
     * thousand. Each one gets the document that named them, which is true, and
     * up to two written lines, which are marked as such. The random source is
     * keyed on the person, so a restart writes the same lines.
     */
    private int backfill(Instant now) {
        List<Person> all = people.findAll();
        Set<UUID> spoken = new HashSet<>();
        for (PersonSignal signal : signals.findByPersonIdIn(all.stream().map(Person::getId).toList())) {
            spoken.add(signal.getPersonId());
        }
        Map<UUID, Company> byId = new LinkedHashMap<>();
        for (Company company : companies.findAll()) {
            byId.put(company.getId(), company);
        }
        List<PersonSignal> rows = new ArrayList<>();
        int filled = 0;
        for (Person person : all) {
            Company company = byId.get(person.getCompanyId());
            if (spoken.contains(person.getId()) || company == null) {
                continue;
            }
            Random random = new Random(SEED ^ ((long) company.getKey().hashCode() << 32 | person.getName().hashCode()));
            if (person.getSourceUrl() != null && !person.getSourceUrl().isBlank()) {
                rows.add(new PersonSignal(UUID.randomUUID(), person.getId(), "NEWS", "SMALL",
                        "Named as " + person.getRole() + " in "
                                + (person.getPublisher() == null || person.getPublisher().isBlank()
                                        ? "a stored document" : person.getPublisher()) + ".",
                        person.getSourceUrl(), person.getPublisher(), person.getPublishedAt(), MENTION, now));
            }
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Said said = say(random, company, now);
                rows.add(new PersonSignal(UUID.randomUUID(), person.getId(), said.kind(), said.strength(),
                        said.text(), null, said.publisher(), said.publishedAt(), ORIGIN, now));
            }
            filled++;
        }
        signals.saveAll(rows);
        return filled;
    }

    private int fromFile(Map<UUID, Set<String>> held, Map<UUID, UUID> anchors, Instant now) {
        CorpusFiles.PeopleFile file = CorpusFiles.read(mapper, properties.peoplePath(), CorpusFiles.PeopleFile.class);
        if (file == null || file.people() == null || file.people().isEmpty()) {
            return 0;
        }
        int added = 0;
        for (CorpusFiles.RawPerson entry : file.people()) {
            if (entry.companyKey() == null || entry.name() == null || entry.name().isBlank()
                    || entry.role() == null || entry.role().isBlank()) {
                log.warn("a seeded person is missing a company, a name or a role");
                continue;
            }
            Company company = companies.findByKey(entry.companyKey()).orElse(null);
            if (company == null) {
                log.warn("no company {} to place {} at", entry.companyKey(), entry.name());
                continue;
            }
            List<Said> said = new ArrayList<>();
            for (CorpusFiles.RawPersonSignal signal : entry.signals() == null
                    ? List.<CorpusFiles.RawPersonSignal>of() : entry.signals()) {
                if (signal.text() == null || signal.text().isBlank()) {
                    continue;
                }
                said.add(new Said(
                        signal.kind() == null || signal.kind().isBlank() ? "NOTE" : signal.kind(),
                        signal.strength() == null || signal.strength().isBlank() ? "SMALL" : signal.strength(),
                        signal.text(), signal.publisher(), instant(signal.publishedAt())));
            }
            added += place(company, entry.name(), entry.role(), said, held, anchors, now);
        }
        return added;
    }

    /**
     * The rest of the hundred, spread by taking every n-th company in key order
     * rather than the first n — a hundred people all at companies beginning with
     * A is not a demo of anything.
     */
    private int invent(int wanted, Map<UUID, Set<String>> held, Map<UUID, UUID> anchors, Instant now) {
        List<Company> all = new ArrayList<>(companies.findAll());
        all.sort(Comparator.comparing(Company::getKey));
        if (all.isEmpty()) {
            return 0;
        }
        Random random = new Random(SEED);
        int stride = Math.max(1, all.size() / Math.max(1, wanted));
        int added = 0;
        for (int at = 0; at < all.size() && added < wanted; at += stride) {
            Company company = all.get(at);
            String name = GIVEN.get(random.nextInt(GIVEN.size())) + " " + FAMILY.get(random.nextInt(FAMILY.size()));
            String role = ROLES.get(random.nextInt(ROLES.size()));
            List<Said> said = new ArrayList<>();
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                said.add(say(random, company, now));
            }
            added += place(company, name, role, said, held, anchors, now);
        }
        return added;
    }

    private Said say(Random random, Company company, Instant now) {
        Template template = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
        String industry = company.getIndustry() == null || company.getIndustry().isBlank()
                ? "operations" : company.getIndustry().toLowerCase(Locale.ROOT);
        String text = template.text()
                .replace("%s", industry)
                .replace("%a", article(industry) + industry);
        return new Said(template.kind(), template.strength(), text, template.publisher(),
                now.minus(random.nextInt(420), ChronoUnit.DAYS));
    }

    /** "an automotive supplier", not "a automotive supplier". */
    private static String article(String word) {
        return "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an " : "a ";
    }

    private int place(Company company, String name, String role, List<Said> said, Map<UUID, Set<String>> held,
                      Map<UUID, UUID> anchors, Instant now) {
        String folded = name.toLowerCase(Locale.ROOT);
        if (held.getOrDefault(company.getId(), Set.of()).contains(folded)) {
            return 0;
        }
        UUID anchor = anchors.computeIfAbsent(company.getId(), this::newestEvidence);
        if (anchor == null) {
            return 0;
        }
        Person person = new Person(UUID.randomUUID(), company.getId(), name, role, anchor, 0, 0, null, null, null, now);
        people.save(person);
        held.computeIfAbsent(company.getId(), key -> new HashSet<>()).add(folded);

        List<PersonSignal> rows = new ArrayList<>();
        for (Said entry : said) {
            rows.add(new PersonSignal(UUID.randomUUID(), person.getId(), entry.kind(), entry.strength(),
                    entry.text(), null, entry.publisher(), entry.publishedAt(), ORIGIN, now));
        }
        signals.saveAll(rows);
        return 1;
    }

    /**
     * One anchor per company, not one per person: the extractor skips a document
     * that already carries a person, so anchoring a whole group to the same page
     * costs that company one document rather than one for each of them.
     */
    private UUID newestEvidence(UUID companyId) {
        Evidence newest = null;
        for (Evidence item : evidence.findByCompanyId(companyId)) {
            Instant anchor = item.decayAnchor();
            if (anchor == null) {
                continue;
            }
            if (newest == null || anchor.isAfter(newest.decayAnchor())) {
                newest = item;
            }
        }
        return newest == null ? null : newest.getId();
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
