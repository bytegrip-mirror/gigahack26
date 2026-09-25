package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.OutreachService;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.web.dto.OutreachDraft;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/outreach")
public class OutreachController {

    private final OutreachService outreach;

    public OutreachController(OutreachService outreach) {
        this.outreach = outreach;
    }

    public record Request(String serviceLine, String channel) {
    }

    @PostMapping("/{companyId}")
    public ResponseEntity<OutreachDraft> draft(@PathVariable UUID companyId,
                                               @RequestBody(required = false) Request request) {
        String serviceLine = request == null || request.serviceLine() == null || request.serviceLine().isBlank()
                ? PolicyLibrary.AUTOMATION
                : request.serviceLine();
        String channel = request == null ? "email" : request.channel();
        return outreach.draft(companyId, serviceLine, channel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<OutreachDraft> preview(@PathVariable UUID companyId,
                                                 @RequestParam(name = "serviceLine", required = false)
                                                 String serviceLine,
                                                 @RequestParam(name = "channel", defaultValue = "email")
                                                 String channel) {
        String line = serviceLine == null || serviceLine.isBlank() ? PolicyLibrary.AUTOMATION : serviceLine;
        return outreach.draft(companyId, line, channel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
