package com.pistonpunk.gigahack26.person.application;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.evidence.application.CorpusProperties;
import com.pistonpunk.gigahack26.evidence.domain.Evidence;
import com.pistonpunk.gigahack26.evidence.infrastructure.CorpusFiles;
import com.pistonpunk.gigahack26.evidence.infrastructure.EvidenceRepository;
import com.pistonpunk.gigahack26.person.domain.Person;
import com.pistonpunk.gigahack26.person.domain.PersonSignal;
import com.pistonpunk.gigahack26.person.infrastructure.PersonRepository;
import com.pistonpunk.gigahack26.person.infrastructure.PersonSignalRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Places the people the demo ships with, and what they have said.
 *
 * People are otherwise only ever found by reading the corpus on request, which
 * leaves the screen empty until somebody presses the button. These arrive with
 * the deployment instead, so the page has something on it on a database that has
 * just been created — and they are marked, so nothing reads as extracted.
 *
 * Runs after {@link CorpusSeeder}, whose companies they attach to.
 */
@Component
@Order(20)
public class PersonSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersonSeeder.class);
    private static final String ORIGIN = "demo-seed";

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
        CorpusFiles.PeopleFile file = CorpusFiles.read(mapper, properties.peoplePath(), CorpusFiles.PeopleFile.class);
        if (file == null || file.people() == null || file.people().isEmpty()) {
            return 0;
        }

        Map<UUID, Set<String>> held = new LinkedHashMap<>();
        for (Person person : people.findAll()) {
            held.computeIfAbsent(person.getCompanyId(), key -> new HashSet<>())
                    .add(person.getName().toLowerCase(Locale.ROOT));
        }

        Map<UUID, UUID> anchors = new LinkedHashMap<>();
        Instant now = clock.instant();
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
            String folded = entry.name().toLowerCase(Locale.ROOT);
            if (held.getOrDefault(company.getId(), Set.of()).contains(folded)) {
                continue;
            }
            UUID anchor = anchors.computeIfAbsent(company.getId(), this::newestEvidence);
            if (anchor == null) {
                log.warn("no document to anchor {} to", entry.name());
                continue;
            }

            Person person = new Person(UUID.randomUUID(), company.getId(), entry.name(), entry.role(),
                    anchor, 0, 0, null, null, null, now);
            people.save(person);
            held.computeIfAbsent(company.getId(), key -> new HashSet<>()).add(folded);
            added += 1;

            List<PersonSignal> rows = new ArrayList<>();
            for (CorpusFiles.RawPersonSignal signal : entry.signals() == null
                    ? List.<CorpusFiles.RawPersonSignal>of() : entry.signals()) {
                if (signal.text() == null || signal.text().isBlank()) {
                    continue;
                }
                rows.add(new PersonSignal(UUID.randomUUID(), person.getId(),
                        signal.kind() == null || signal.kind().isBlank() ? "NOTE" : signal.kind(),
                        signal.strength() == null || signal.strength().isBlank() ? "SMALL" : signal.strength(),
                        signal.text(), signal.sourceUrl(), signal.publisher(), instant(signal.publishedAt()),
                        ORIGIN, now));
            }
            signals.saveAll(rows);
        }
        if (added > 0) {
            log.info("placed {} seeded people with their signals", added);
        }
        return added;
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
