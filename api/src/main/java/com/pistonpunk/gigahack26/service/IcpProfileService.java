package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.IcpProfile;
import com.pistonpunk.gigahack26.domain.Lead;
import com.pistonpunk.gigahack26.domain.LeadStatus;
import com.pistonpunk.gigahack26.domain.Signal;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.IcpProfileRepository;
import com.pistonpunk.gigahack26.repository.jpa.LeadRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The named books of business, and who falls into them.
 *
 * A profile is a description, never a gate: nothing here disqualifies a company
 * or touches a score. It decides one thing — which companies carry its name — so
 * a company's number is the same whichever profile you look at it through.
 */
@Service
public class IcpProfileService {

    public record Draft(String name, String countries, String regions, String industries,
                        int minEmployees, String sizeBands, String questionKeys, boolean excludeCompetitors,
                        boolean untouchedOnly, boolean active) {
    }

    public record Match(long companies, List<String> sample) {
    }

    private static final int SAMPLE = 8;

    private final IcpProfileRepository profiles;
    private final CompanyRepository companies;
    private final RegionService regions;
    private final LeadRepository leads;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final Clock clock;

    public IcpProfileService(IcpProfileRepository profiles, CompanyRepository companies, RegionService regions,
                             LeadRepository leads, SignalRepository signals, PolicyQuestionRepository questions,
                             Clock clock) {
        this.profiles = profiles;
        this.companies = companies;
        this.regions = regions;
        this.leads = leads;
        this.signals = signals;
        this.questions = questions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<IcpProfile> list() {
        return profiles.findByOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<IcpProfile> active() {
        return list().stream().filter(IcpProfile::isActive).toList();
    }

    @Transactional
    public IcpProfile create(Draft draft) {
        return profiles.save(probe(draft, list().size(), draft.active()));
    }

    @Transactional
    public Optional<IcpProfile> update(UUID id, Draft draft) {
        return profiles.findById(id).map(profile -> {
            profile.revise(name(draft), clean(draft.countries()), clean(draft.regions()),
                    clean(draft.industries()), Math.max(0, draft.minEmployees()), clean(draft.sizeBands()),
                    keys(draft.questionKeys()), draft.excludeCompetitors(), draft.untouchedOnly(), draft.active(),
                    profile.getSortOrder());
            return profiles.save(profile);
        });
    }

    @Transactional
    public void delete(UUID id) {
        profiles.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<String>> membership(List<Company> targets) {
        List<IcpProfile> active = active();
        Map<UUID, List<String>> byCompany = new LinkedHashMap<>();
        if (active.isEmpty() || targets.isEmpty()) {
            return byCompany;
        }
        Map<UUID, Boolean> worked = worked(targets);
        Map<UUID, Set<String>> signalled = signalled(targets);
        Map<UUID, Set<String>> geography = new LinkedHashMap<>();
        for (IcpProfile profile : active) {
            geography.put(profile.getId(), geography(profile));
        }
        for (Company company : targets) {
            boolean touched = worked.getOrDefault(company.getId(), false);
            Set<String> fired = signalled.getOrDefault(company.getId(), Set.of());
            List<String> names = new ArrayList<>();
            for (IcpProfile profile : active) {
                if (profile.covers(company, geography.get(profile.getId()), touched, fired)) {
                    names.add(profile.getName());
                }
            }
            if (!names.isEmpty()) {
                byCompany.put(company.getId(), List.copyOf(names));
            }
        }
        return byCompany;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> sizes(List<Company> targets) {
        Map<UUID, Boolean> worked = worked(targets);
        Map<UUID, Set<String>> signalled = signalled(targets);
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (IcpProfile profile : list()) {
            Set<String> geography = geography(profile);
            counts.put(profile.getId(), targets.stream()
                    .filter(company -> profile.covers(company, geography,
                            worked.getOrDefault(company.getId(), false),
                            signalled.getOrDefault(company.getId(), Set.of())))
                    .count());
        }
        return counts;
    }

    /**
     * What a profile would hold, before it is saved — the same predicate the board
     * uses, so the number on the screen and the number on the board cannot drift.
     */
    @Transactional(readOnly = true)
    public Match preview(Draft draft) {
        List<Company> all = companies.findAll();
        if (all.isEmpty()) {
            return new Match(0, List.of());
        }
        IcpProfile probe = probe(draft, 0, false);
        Set<String> geography = geography(probe);
        Map<UUID, Boolean> worked = worked(all);
        Map<UUID, Set<String>> signalled = signalled(all);
        List<String> sample = new ArrayList<>();
        long total = 0;
        for (Company company : all) {
            if (probe.covers(company, geography, worked.getOrDefault(company.getId(), false),
                    signalled.getOrDefault(company.getId(), Set.of()))) {
                total += 1;
                if (sample.size() < SAMPLE) {
                    sample.add(company.getName());
                }
            }
        }
        return new Match(total, List.copyOf(sample));
    }

    private IcpProfile probe(Draft draft, int sortOrder, boolean active) {
        return new IcpProfile(UUID.randomUUID(), name(draft), clean(draft.countries()),
                clean(draft.regions()), clean(draft.industries()), Math.max(0, draft.minEmployees()),
                clean(draft.sizeBands()), keys(draft.questionKeys()), draft.excludeCompetitors(),
                draft.untouchedOnly(), active, sortOrder, clock.instant());
    }

    /**
     * Which question keys fired for each company.
     *
     * The questions are loaded once and read through a map rather than joined:
     * a signal carries a question id and the key lives on the question, and one
     * `findByActiveTrue()` is cheaper than a join per company. Companies with
     * nothing are absent, the same shape `worked` uses.
     */
    private Map<UUID, Set<String>> signalled(List<Company> targets) {
        Map<UUID, Set<String>> byCompany = new LinkedHashMap<>();
        if (targets.isEmpty()) {
            return byCompany;
        }
        Map<UUID, String> keyById = questions.findByActiveTrue().stream()
                .collect(Collectors.toMap(question -> question.getId(), question -> question.getKey(),
                        (first, second) -> first));
        if (keyById.isEmpty()) {
            return byCompany;
        }
        List<UUID> ids = targets.stream().map(Company::getId).toList();
        for (Signal signal : signals.findByCompanyIdIn(ids)) {
            String key = keyById.get(signal.getQuestionId());
            if (key != null) {
                byCompany.computeIfAbsent(signal.getCompanyId(), id -> new LinkedHashSet<>()).add(key);
            }
        }
        return byCompany;
    }

    /**
     * Question keys are identifiers, not words: no case folding, no country-code
     * shouting. Only trimmed, deduped and capped to fit the column.
     */
    private static String keys(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .limit(40)
                .collect(Collectors.joining(","));
    }

    /** A profile's geography is what it names outright plus every region it picked,
     * so "Western Europe" costs one choice rather than twelve country codes. */
    private Set<String> geography(IcpProfile profile) {
        Set<String> wanted = new LinkedHashSet<>(profile.namedCountries());
        for (String region : words(profile.getRegions())) {
            for (String country : regions.countriesIn(region)) {
                wanted.add(country.toUpperCase(Locale.ROOT));
            }
        }
        return wanted;
    }

    private Map<UUID, Boolean> worked(List<Company> targets) {
        Map<UUID, Boolean> byCompany = new LinkedHashMap<>();
        if (targets.isEmpty()) {
            return byCompany;
        }
        for (Lead lead : leads.findByCompanyIdIn(targets.stream().map(Company::getId).toList())) {
            if (lead.getStatus() != null && lead.getStatus() != LeadStatus.NEW) {
                byCompany.put(lead.getCompanyId(), true);
            }
        }
        return byCompany;
    }

    private static String name(Draft draft) {
        String value = draft.name() == null ? "" : draft.name().trim();
        return value.isEmpty() ? "Untitled profile" : value.substring(0, Math.min(120, value.length()));
    }


    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toUpperCase(Locale.ROOT).length() == 2 ? entry.toUpperCase(Locale.ROOT) : entry)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static Set<String> words(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
