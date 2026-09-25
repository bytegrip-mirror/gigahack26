package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.domain.Polarity;
import com.pistonpunk.gigahack26.domain.WeightTier;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.service.RescoreService;
import com.pistonpunk.gigahack26.web.dto.RescoreResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rescore")
public class RescoreController {

    private final RescoreService rescore;

    public RescoreController(RescoreService rescore) {
        this.rescore = rescore;
    }

    public record Request(String serviceLine, UUID questionId, String question, String engine,
                          WeightTier tier, Polarity polarity, String theme, String instructions) {
    }

    @PostMapping
    public RescoreResponse rescore(@RequestBody(required = false) Request request) {
        String serviceLine = request == null || request.serviceLine() == null || request.serviceLine().isBlank()
                ? PolicyLibrary.AUTOMATION
                : request.serviceLine();
        UUID questionId = request == null ? null : request.questionId();
        String label = request == null ? null : request.question();
        String engine = request == null ? null : request.engine();
        WeightTier tier = request == null ? null : request.tier();
        Polarity polarity = request == null ? null : request.polarity();
        String theme = request == null ? null : request.theme();
        String instructions = request == null ? null : request.instructions();
        return rescore.rescore(serviceLine, questionId, label, engine, tier, polarity, theme, instructions);
    }
}
