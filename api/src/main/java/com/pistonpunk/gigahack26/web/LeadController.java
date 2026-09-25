package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.LeadStatus;
import com.pistonpunk.gigahack26.service.LeadService;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.web.dto.LeadView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leads;
    private final Clock clock;

    public LeadController(LeadService leads, Clock clock) {
        this.leads = leads;
        this.clock = clock;
    }

    public record RecordRequest(
            String serviceLine,
            LeadStatus status,
            @Size(max = 20) String channel,
            @Size(max = 1000) String note,
            @Size(max = 400) String outreachSubject) {
    }

    @GetMapping
    public List<LeadView> list(@RequestParam(name = "serviceLine", required = false) String serviceLine) {
        return leads.list(line(serviceLine));
    }

    @GetMapping("/tally")
    public Map<String, Object> tally(@RequestParam(name = "serviceLine", required = false) String serviceLine) {
        Map<String, Object> summary = new LinkedHashMap<>(leads.tally(line(serviceLine)));
        summary.put("crmConfigured", leads.crmConfigured());
        summary.put("checkedAt", clock.instant());
        return summary;
    }

    @PostMapping("/{companyId}")
    public LeadView record(@PathVariable UUID companyId, @Valid @RequestBody RecordRequest request) {
        leads.record(companyId, line(request.serviceLine()), request.status(), request.channel(), request.note(),
                request.outreachSubject());
        return leads.list(line(request.serviceLine())).stream()
                .filter(view -> view.companyId().equals(companyId))
                .findFirst()
                .orElseThrow();
    }

    @PostMapping("/{companyId}/hubspot")
    public ResponseEntity<Map<String, Object>> pushToHubspot(@PathVariable UUID companyId,
                                                             @RequestBody(required = false) RecordRequest request) {
        String serviceLine = line(request == null ? null : request.serviceLine());
        if (!leads.crmConfigured()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pushed", false);
            body.put("queued", true);
            body.put("reason", "HUBSPOT_TOKEN is not configured on this instance");
            body.put("detail", "The lead is recorded here and the push is queued until a token is set.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }

        leads.record(companyId, serviceLine, LeadStatus.CONTACTED, "hubspot", null, null);
        leads.markSynced(companyId, serviceLine);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pushed", true);
        body.put("companyId", companyId);
        body.put("syncedAt", clock.instant());
        return ResponseEntity.ok(body);
    }

    private static String line(String serviceLine) {
        return serviceLine == null || serviceLine.isBlank() ? PolicyLibrary.AUTOMATION : serviceLine;
    }
}
