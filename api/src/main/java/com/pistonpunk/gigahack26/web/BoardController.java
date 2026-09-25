package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.BoardService;
import com.pistonpunk.gigahack26.service.CaptureStore;
import com.pistonpunk.gigahack26.service.LeadService;
import com.pistonpunk.gigahack26.service.PolicyLibrary;
import com.pistonpunk.gigahack26.service.ScoringProperties;
import com.pistonpunk.gigahack26.service.ScoreboardService;
import com.pistonpunk.gigahack26.web.dto.BoardRow;
import com.pistonpunk.gigahack26.web.dto.CompanyDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BoardController {

    private final BoardService board;
    private final ScoreboardService scoreboard;
    private final ScoringProperties scoring;
    private final CaptureStore captures;
    private final LeadService leads;

    public BoardController(BoardService board, ScoreboardService scoreboard, ScoringProperties scoring,
                           CaptureStore captures, LeadService leads) {
        this.board = board;
        this.scoreboard = scoreboard;
        this.scoring = scoring;
        this.captures = captures;
        this.leads = leads;
    }

    @GetMapping("/companies")
    public List<BoardRow> companies(@RequestParam(name = "serviceLine", required = false) String serviceLine,
                                    @RequestParam(name = "filter", defaultValue = "all") String filter,
                                    @RequestParam(name = "sort", defaultValue = "score") String sort,
                                    @RequestParam(name = "q", required = false) String query,
                                    @RequestParam(name = "stage", required = false) String stage) {
        return board.board(line(serviceLine), filter, sort, query, stage);
    }

    @GetMapping("/companies/{id}")
    public ResponseEntity<CompanyDetail> company(@PathVariable UUID id,
                                                 @RequestParam(name = "serviceLine", required = false)
                                                 String serviceLine) {
        return board.detail(id, line(serviceLine))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/meta")
    public Map<String, Object> meta(@RequestParam(name = "serviceLine", required = false) String serviceLine) {
        String line = line(serviceLine);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serviceLines", scoreboard.serviceLines());
        meta.put("defaultServiceLine", PolicyLibrary.AUTOMATION);
        meta.put("serviceLine", line);
        meta.put("engine", board.primaryEngine());
        meta.put("requestedEngine", board.requestedEngine());
        meta.put("languageModelAvailable", board.languageModelAvailable());
        meta.put("localEngine", board.localEngineId());
        meta.put("degraded", board.degraded());
        meta.put("bands", board.bandCounts(line));
        meta.put("stages", leads.tally(line));
        meta.put("captures", captures.size());
        meta.put("scoring", Map.of(
                "baseRate", scoring.baseRate(),
                "baseLogit", round(scoring.baseLogit()),
                "priorityThreshold", scoring.priorityThreshold(),
                "nurtureThreshold", scoring.nurtureThreshold(),
                "watchThreshold", scoring.watchThreshold(),
                "themeCap", round(scoring.themeCap()),
                "rankDiscount", scoring.rankDiscount(),
                "icpMinEmployees", scoring.icpMinEmployees()));
        return meta;
    }

    private static String line(String serviceLine) {
        return serviceLine == null || serviceLine.isBlank() ? PolicyLibrary.AUTOMATION : serviceLine;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
