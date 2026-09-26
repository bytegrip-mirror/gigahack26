package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.WeightTier;
import com.pistonpunk.gigahack26.service.AskService;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.service.RescoreService;
import com.pistonpunk.gigahack26.web.dto.RescoreResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AskService ask;
    private final RescoreService rescore;

    public AskController(AskService ask, RescoreService rescore) {
        this.ask = ask;
        this.rescore = rescore;
    }

    public record Request(String serviceLine, String question, String engine, WeightTier tier,
                          Polarity polarity, String theme) {
    }

    @GetMapping("/read")
    public Map<String, Object> read(@RequestParam(name = "q", required = false) String question) {
        return view(ask.interpret(question));
    }

    @PostMapping
    public Map<String, Object> ask(@RequestBody(required = false) Request request) {
        String serviceLine = request == null || request.serviceLine() == null || request.serviceLine().isBlank()
                ? PolicyLibrary.AUTOMATION
                : request.serviceLine();
        String question = request == null ? null : request.question();
        AskService.Interpretation interpretation = ask.interpret(question);
        Map<String, Object> response = new LinkedHashMap<>(view(interpretation));
        response.put("serviceLine", serviceLine);

        if (!interpretation.facets().isEmpty()) {
            List<UUID> matched = List.copyOf(interpretation.matched());
            response.put("matchedCompanies", matched.size());
            response.put("companyIds", matched);
        }

        if (interpretation.kind().equals("SIGNAL") || interpretation.kind().equals("FILTER_AND_SIGNAL")) {
            RescoreResponse scored = rescore.rescore(
                    serviceLine,
                    null,
                    interpretation.kind().equals("SIGNAL") ? question : interpretation.question(),
                    request == null ? null : request.engine(),
                    request == null ? null : request.tier(),
                    request == null ? null : request.polarity(),
                    request == null ? null : request.theme(),
                    null);
            response.put("movedRows", scored.movedRows());
            response.put("changedTop", scored.changedTop());
            response.put("tookMs", scored.tookMs());
            response.put("documentsScanned", scored.documentsScanned());
            response.put("signalsProduced", scored.signalsProduced());
            response.put("keptPrevious", scored.keptPrevious());
            response.put("board", scored.board());
            response.put("moves", scored.moves());
            response.put("question", interpretation.kind().equals("SIGNAL") ? question : interpretation.question());
        }
        return response;
    }

    private static Map<String, Object> view(AskService.Interpretation interpretation) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", interpretation.kind());
        out.put("reading", interpretation.reading());
        out.put("question", interpretation.question());
        out.put("facets", interpretation.facets().stream()
                .map(facet -> Map.of(
                        "kind", facet.kind(),
                        "value", facet.value(),
                        "label", facet.label(),
                        "matches", facet.matches()))
                .toList());
        return out;
    }
}
