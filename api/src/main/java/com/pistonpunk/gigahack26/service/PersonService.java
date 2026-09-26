package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.Evidence;
import com.pistonpunk.gigahack26.domain.LeadStatus;
import com.pistonpunk.gigahack26.domain.Person;
import com.pistonpunk.gigahack26.domain.PersonSignal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PersonRepository;
import com.pistonpunk.gigahack26.repository.jpa.PersonSignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The people the corpus names, read out of the documents it already holds.
 *
 * A person is only ever placed where a document placed them: the name, the role
 * and the sentence they came from are all one byte range in a stored page, the
 * same rule the signals follow. Nothing here guesses that somebody works
 * somewhere — if the page does not say so, the desk does not either.
 */
@Service
public class PersonService {

    private final PersonRepository people;
    private final PersonSignalRepository signals;
    private final EvidenceRepository evidence;
    private final CompanyRepository companies;
    private final CaptureStore captures;
    private final Clock clock;

    public PersonService(PersonRepository people, PersonSignalRepository signals, EvidenceRepository evidence,
                         CompanyRepository companies, CaptureStore captures, Clock clock) {
        this.people = people;
        this.signals = signals;
        this.evidence = evidence;
        this.companies = companies;
        this.captures = captures;
        this.clock = clock;
    }

    /**
     * What each of these people has said, strongest first, so the caller does not
     * have to rank them and every screen shows the same one at the top.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<PersonSignal>> signalsFor(List<UUID> personIds) {
        Map<UUID, List<PersonSignal>> out = new LinkedHashMap<>();
        if (personIds.isEmpty()) {
            return out;
        }
        for (PersonSignal signal : signals.findByPersonIdIn(personIds)) {
            out.computeIfAbsent(signal.getPersonId(), key -> new ArrayList<>()).add(signal);
        }
        for (List<PersonSignal> held : out.values()) {
            held.sort((left, right) -> rank(right.getStrength()) - rank(left.getStrength()));
        }
        return out;
    }

    private static int rank(String strength) {
        if (strength == null) {
            return 0;
        }
        return switch (strength.toUpperCase(Locale.ROOT)) {
            case "STRONG" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    @Transactional(readOnly = true)
    public List<Person> forCompanies(List<UUID> companyIds) {
        return companyIds.isEmpty() ? List.of() : people.findByCompanyIdIn(companyIds);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> companyNames() {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Company company : companies.findAll()) {
            names.put(company.getId(), company.getName());
        }
        return names;
    }

    /**
     * Reads every document held on these companies and keeps the people it names.
     * A document is read once: re-reading one that has not changed cannot produce a
     * different person, and the cost is the whole corpus otherwise.
     */
    @Transactional
    public int extract(List<Company> targets) {
        Set<UUID> alreadyRead = new HashSet<>();
        for (Person person : people.findAll()) {
            alreadyRead.add(person.getEvidenceId());
        }

        Map<UUID, Company> byId = new LinkedHashMap<>();
        for (Company company : targets) {
            byId.put(company.getId(), company);
        }

        Map<UUID, Set<String>> held = new LinkedHashMap<>();
        for (Person person : people.findAll()) {
            held.computeIfAbsent(person.getCompanyId(), key -> new HashSet<>())
                    .add(person.getName().toLowerCase(Locale.ROOT));
        }

        Map<String, Person> found = new LinkedHashMap<>();
        for (Evidence item : evidence.findAll()) {
            Company company = byId.get(item.getCompanyId());
            if (company == null || alreadyRead.contains(item.getId())) {
                continue;
            }
            String text = captures.text(item.getKey());
            if (text == null || text.isBlank()) {
                continue;
            }
            for (PersonExtractor.Found person : PersonExtractor.find(text)) {
                String folded = person.name().toLowerCase(Locale.ROOT);
                if (folded.contains(company.getName().toLowerCase(Locale.ROOT))
                        || held.getOrDefault(company.getId(), Set.of()).contains(folded)) {
                    continue;
                }
                String key = company.getId() + "|" + folded;
                Person earlier = found.get(key);
                if (earlier != null && !isNewer(item, earlier)) {
                    continue;
                }
                found.put(key, new Person(UUID.randomUUID(), company.getId(), person.name(), person.role(),
                        item.getId(), person.start(), person.end(), item.getSourceUrl(), item.getPublisher(),
                        item.getPublishedAt(), clock.instant()));
            }
        }
        people.saveAll(found.values());
        return found.size();
    }

    /** One row per person per company: the same name in two documents is one contact. */
    private static boolean isNewer(Evidence item, Person earlier) {
        if (item.getPublishedAt() == null) {
            return false;
        }
        return earlier.getPublishedAt() == null || item.getPublishedAt().isAfter(earlier.getPublishedAt());
    }

    @Transactional
    public void forget() {
        signals.deleteAll();
        people.deleteAll();
    }

    @Transactional(readOnly = true)
    public long count() {
        return people.count();
    }

    @Transactional
    public Optional<Person> setEmail(UUID id, String email) {
        return people.findById(id).map(person -> {
            person.setEmail(email);
            return people.save(person);
        });
    }

    /** The people at one company, the ones with an address first. */
    @Transactional(readOnly = true)
    public List<Person> contactsFor(UUID companyId) {
        return people.findByCompanyIdIn(List.of(companyId)).stream()
                .sorted(java.util.Comparator
                        .comparing((Person person) -> person.getEmail() == null ? 1 : 0)
                        .thenComparing(Person::getName))
                .toList();
    }

    @Transactional
    public Optional<Person> move(UUID id, String rawStatus, String channel, String subject) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        LeadStatus next;
        try {
            next = LeadStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
        return people.findById(id).map(person -> {
            person.moveTo(next, channel, subject, clock.instant());
            return people.save(person);
        });
    }

    @Transactional
    public Optional<Person> enrich(UUID id, EnrichmentService enrichment) {
        if (!enrichment.available()) {
            return Optional.empty();
        }
        return people.findById(id).map(person -> {
            String domain = domains().get(person.getCompanyId());
            enrichment.enrich(person, domain).ifPresent(found -> {
                if (found.found()) {
                    person.enrich(found.linkedinUrl(), found.headline(), clock.instant());
                    if (person.getEmail() == null) {
                        person.setEmail(found.email());
                    }
                }
            });
            return people.save(person);
        });
    }

    /**
     * Enriches the people at the companies a rep is actually looking at, in the
     * order the board puts them, and never the same person twice.
     */
    @Transactional
    public Map<String, Object> enrichAll(String serviceLine, int limit, EnrichmentService enrichment) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("provider", enrichment.provider());
        report.put("available", enrichment.available());
        if (!enrichment.available()) {
            report.put("enriched", 0);
            return report;
        }
        Map<UUID, String> domains = domains();
        int done = 0;
        int missed = 0;
        for (Person person : people.findByEnrichedAtIsNull()) {
            if (done + missed >= limit) {
                break;
            }
            String domain = domains.get(person.getCompanyId());
            Optional<EnrichmentService.Enriched> found = enrichment.enrich(person, domain);
            if (found.isEmpty()) {
                continue;
            }
            if (found.get().found()) {
                person.enrich(found.get().linkedinUrl(), found.get().headline(), clock.instant());
                if (person.getEmail() == null) {
                    person.setEmail(found.get().email());
                }
                people.save(person);
                done += 1;
            } else {
                // Remember the miss as an empty answer, or the same name is asked
                // for again on every pass and the provider is paid twice.
                person.enrich(null, null, clock.instant());
                people.save(person);
                missed += 1;
            }
        }
        report.put("enriched", done);
        report.put("notFound", missed);
        return report;
    }

    private Map<UUID, String> domains() {
        Map<UUID, String> domains = new LinkedHashMap<>();
        for (Company company : companies.findAll()) {
            domains.put(company.getId(), company.getDomain());
        }
        return domains;
    }
}
