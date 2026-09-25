package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.Region;
import com.pistonpunk.gigahack26.repository.jpa.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RegionService {

    private final RegionRepository regions;
    private final Clock clock;

    public RegionService(RegionRepository regions, Clock clock) {
        this.regions = regions;
        this.clock = clock;
    }

    @Transactional
    public void ensureSeeded() {
        int order = 0;
        for (RegionTaxonomy.Definition definition : RegionTaxonomy.definitions()) {
            order += 10;
            if (regions.findByKey(definition.key()).isPresent()) {
                continue;
            }
            regions.save(new Region(
                    UUID.randomUUID(),
                    definition.key(),
                    definition.name(),
                    definition.parentKey() == null ? Region.Level.CONTINENT : Region.Level.REGION,
                    definition.parentKey(),
                    definition.countries(),
                    order,
                    clock.instant()));
        }
    }

    public record Node(String key, String name, List<Node> children) {
    }

    public record Country(String code, String regionKey, String regionName, String continentKey) {
    }

    @Transactional(readOnly = true)
    public List<Node> tree() {
        List<Region> all = regions.findByActiveTrueOrderBySortOrderAscNameAsc();
        List<Node> continents = new ArrayList<>();
        for (Region parent : all) {
            if (!parent.isContinent()) {
                continue;
            }
            List<Node> children = all.stream()
                    .filter(child -> parent.getKey().equals(child.getParentKey()))
                    .map(child -> new Node(child.getKey(), child.getName(), List.of()))
                    .toList();
            continents.add(new Node(parent.getKey(), parent.getName(), children));
        }
        return continents;
    }

    @Transactional(readOnly = true)
    public List<Country> countries() {
        List<Region> all = regions.findByActiveTrueOrderBySortOrderAscNameAsc();
        Map<String, Region> byKey = new LinkedHashMap<>();
        all.forEach(region -> byKey.put(region.getKey(), region));

        List<Country> out = new ArrayList<>();
        for (Region region : all) {
            if (region.isContinent()) {
                continue;
            }
            for (String code : region.countryList()) {
                out.add(new Country(code, region.getKey(), region.getName(), region.getParentKey()));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Set<String> countriesIn(String regionKey) {
        if (regionKey == null || regionKey.isBlank() || regionKey.equalsIgnoreCase("world")) {
            return Set.of();
        }
        Optional<Region> region = regions.findByKey(regionKey);
        if (region.isEmpty()) {
            return Set.of();
        }
        Region found = region.get();
        if (!found.isContinent()) {
            return new LinkedHashSet<>(found.countryList());
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Region child : regions.findByParentKeyOrderBySortOrderAscNameAsc(found.getKey())) {
            codes.addAll(child.countryList());
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public Optional<String> regionForCountry(String countryCode) {
        if (countryCode == null) {
            return Optional.empty();
        }
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        return regions.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(region -> !region.isContinent())
                .filter(region -> region.countryList().contains(code))
                .map(Region::getKey)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Region> all() {
        return regions.findAllByOrderBySortOrderAscNameAsc();
    }
}
