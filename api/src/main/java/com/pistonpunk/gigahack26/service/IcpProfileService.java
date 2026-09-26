package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.IcpProfile;
import com.pistonpunk.gigahack26.repository.jpa.IcpProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class IcpProfileService {

    public record Draft(String name, String countries, String industries, int minEmployees, boolean active) {
    }

    private final IcpProfileRepository profiles;
    private final Clock clock;

    public IcpProfileService(IcpProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
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
        return profiles.save(new IcpProfile(
                UUID.randomUUID(),
                name(draft),
                clean(draft.countries()),
                clean(draft.industries()),
                Math.max(0, draft.minEmployees()),
                draft.active(),
                list().size(),
                clock.instant()));
    }

    @Transactional
    public Optional<IcpProfile> update(UUID id, Draft draft) {
        return profiles.findById(id).map(profile -> {
            profile.revise(name(draft), clean(draft.countries()), clean(draft.industries()),
                    Math.max(0, draft.minEmployees()), draft.active(), profile.getSortOrder());
            return profiles.save(profile);
        });
    }

    @Transactional
    public void delete(UUID id) {
        profiles.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<String>> membership(List<Company> companies) {
        List<IcpProfile> active = active();
        Map<UUID, List<String>> byCompany = new LinkedHashMap<>();
        if (active.isEmpty()) {
            return byCompany;
        }
        for (Company company : companies) {
            List<String> names = new ArrayList<>();
            for (IcpProfile profile : active) {
                if (profile.covers(company)) {
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
    public Map<UUID, Long> sizes(List<Company> companies) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (IcpProfile profile : list()) {
            counts.put(profile.getId(),
                    companies.stream().filter(profile::covers).count());
        }
        return counts;
    }

    private static String name(Draft draft) {
        String value = draft.name() == null ? "" : draft.name().trim();
        return value.isEmpty() ? "Untitled profile" : value.substring(0, Math.min(120, value.length()));
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toUpperCase(Locale.ROOT).length() == 2 ? entry.toUpperCase(Locale.ROOT) : entry)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
