package com.pistonpunk.gigahack26.settings.web;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.settings.application.IcpProfileService;
import com.pistonpunk.gigahack26.settings.domain.IcpProfile;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/icp-profiles")
public class IcpProfileController {

    public record Request(String name, String countries, String industries, int minEmployees, boolean active) {
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
        return new IcpProfileService.Draft(request.name(), request.countries(), request.industries(),
                request.minEmployees(), request.active());
    }

    private static IcpProfileView view(IcpProfile profile, long companies) {
        return new IcpProfileView(
                profile.getId(),
                profile.getName(),
                split(profile.getCountries()),
                split(profile.getIndustries()),
                profile.getMinEmployees(),
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
