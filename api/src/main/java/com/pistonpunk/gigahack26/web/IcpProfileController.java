package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Company;
import com.pistonpunk.gigahack26.domain.IcpProfile;
import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.service.IcpProfileService;
import com.pistonpunk.gigahack26.web.dto.IcpProfileView;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/icp-profiles")
public class IcpProfileController {

    public record Request(String name, String color, String countries, String regions, String industries,
                          int minEmployees, String sizeBands, boolean excludeCompetitors, boolean untouchedOnly,
                          boolean active) {
    }

    private final IcpProfileService service;
    private final CompanyRepository companies;

    public IcpProfileController(IcpProfileService service, CompanyRepository companies) {
        this.service = service;
        this.companies = companies;
    }

    @GetMapping
    public List<IcpProfileView> list() {
        List<Company> all = companies.findAll();
        Map<UUID, Long> sizes = service.sizes(all);
        return service.list().stream().map(profile -> view(profile, sizes.getOrDefault(profile.getId(), 0L))).toList();
    }

    /**
     * What the criteria would hold before they are saved. The editor's count and
     * the board's rows come from the same predicate, so they cannot disagree.
     */
    @GetMapping("/preview")
    public IcpProfileService.Match preview(@RequestParam(name = "countries", required = false) String countries,
                                           @RequestParam(name = "regions", required = false) String regions,
                                           @RequestParam(name = "industries", required = false) String industries,
                                           @RequestParam(name = "minEmployees", defaultValue = "0") int minEmployees,
                                           @RequestParam(name = "sizeBands", required = false) String sizeBands,
                                           @RequestParam(name = "excludeCompetitors", defaultValue = "false")
                                           boolean excludeCompetitors,
                                           @RequestParam(name = "untouchedOnly", defaultValue = "false")
                                           boolean untouchedOnly) {
        return service.preview(new IcpProfileService.Draft(null, null, countries, regions, industries,
                minEmployees, sizeBands, excludeCompetitors, untouchedOnly, true));
    }

    @PostMapping
    @CacheEvict(value = "board", allEntries = true)
    public IcpProfileView create(@Valid @RequestBody Request request) {
        return view(service.create(draft(request)), 0L);
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "board", allEntries = true)
    public ResponseEntity<IcpProfileView> update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        return service.update(id, draft(request))
                .map(profile -> ResponseEntity.ok(view(profile, 0L)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "board", allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static IcpProfileService.Draft draft(Request request) {
        return new IcpProfileService.Draft(request.name(), request.color(), request.countries(), request.regions(),
                request.industries(), request.minEmployees(), request.sizeBands(), request.excludeCompetitors(),
                request.untouchedOnly(), request.active());
    }

    private static IcpProfileView view(IcpProfile profile, long companies) {
        return new IcpProfileView(
                profile.getId(),
                profile.getName(),
                profile.getColor(),
                split(profile.getCountries()),
                split(profile.getRegions()),
                split(profile.getIndustries()),
                profile.getMinEmployees(),
                split(profile.getSizeBands()),
                profile.excludesCompetitors(),
                profile.untouchedOnly(),
                profile.isActive(),
                profile.getSortOrder(),
                companies);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty()).toList();
    }
}
