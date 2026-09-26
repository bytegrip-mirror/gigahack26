package com.pistonpunk.gigahack26.studio.web;

import com.pistonpunk.gigahack26.classification.application.LocalRubricEngine;
import com.pistonpunk.gigahack26.policy.domain.DecayClass;
import com.pistonpunk.gigahack26.policy.domain.Polarity;
import com.pistonpunk.gigahack26.policy.domain.WeightTier;
import com.pistonpunk.gigahack26.studio.application.PolicyRequest;
import com.pistonpunk.gigahack26.studio.application.PolicyService;
import com.pistonpunk.gigahack26.studio.application.PolicyView;
import com.pistonpunk.gigahack26.studio.application.PreviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policies;

    public PolicyController(PolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public List<PolicyView> list(@RequestParam(name = "serviceLine", required = false) String serviceLine) {
        return policies.list(serviceLine);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyView create(@Valid @RequestBody PolicyRequest request) {
        return policies.create(request);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    @PatchMapping("/{id}")
    public ResponseEntity<PolicyView> update(@PathVariable UUID id, @Valid @RequestBody PolicyRequest request) {
        return policies.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return policies.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/preview")
    public PreviewResponse preview(@RequestParam(name = "serviceLine") String serviceLine,
                                   @RequestParam(name = "text") String text,
                                   @RequestParam(name = "instructions", required = false) String instructions,
                                   @RequestParam(name = "tier", required = false) WeightTier tier,
                                   @RequestParam(name = "polarity", required = false) Polarity polarity,
                                   @RequestParam(name = "theme", required = false) String theme) {
        return policies.preview(serviceLine, text, instructions, tier, polarity, theme);
    }

    @GetMapping("/derive")
    public List<String> derive(@RequestParam(name = "text") String text,
                               @RequestParam(name = "instructions", required = false) String instructions) {
        return LocalRubricEngine.deriveCues(text, instructions == null ? "" : instructions);
    }

    @GetMapping("/export")
    public List<PolicyRequest> export(@RequestParam(name = "serviceLine", required = false) String serviceLine) {
        return policies.export(serviceLine);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "board", allEntries = true)
    @PostMapping("/import")
    public PolicyService.ImportSummary importPolicies(@RequestBody List<PolicyRequest> incoming) {
        return policies.importPolicies(incoming);
    }

    @GetMapping("/themes")
    public List<String> themes() {
        return policies.knownThemes();
    }

    @GetMapping("/decay-classes")
    public List<Map<String, Object>> decayClasses() {
        return Arrays.stream(DecayClass.values())
                .map(value -> Map.<String, Object>of(
                        "name", value.name(),
                        "halfLifeDays", value.halfLifeDays()))
                .toList();
    }
}
