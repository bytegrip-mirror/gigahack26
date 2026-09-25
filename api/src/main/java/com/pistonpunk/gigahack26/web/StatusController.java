package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.repository.jpa.CompanyRepository;
import com.pistonpunk.gigahack26.repository.jpa.EvidenceRepository;
import com.pistonpunk.gigahack26.repository.jpa.PolicyQuestionRepository;
import com.pistonpunk.gigahack26.repository.jpa.SignalRepository;
import com.pistonpunk.gigahack26.service.BoardService;
import com.pistonpunk.gigahack26.service.CaptureStore;
import com.pistonpunk.gigahack26.web.dto.StatusResponse;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;
    private final CompanyRepository companies;
    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final PolicyQuestionRepository questions;
    private final CaptureStore captures;
    private final BoardService board;
    private final Clock clock;

    public StatusController(JdbcTemplate jdbc, MongoTemplate mongo, CompanyRepository companies,
                            EvidenceRepository evidence, SignalRepository signals,
                            PolicyQuestionRepository questions, CaptureStore captures, BoardService board,
                            Clock clock) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.companies = companies;
        this.evidence = evidence;
        this.signals = signals;
        this.questions = questions;
        this.captures = captures;
        this.board = board;
        this.clock = clock;
    }

    @GetMapping
    public StatusResponse status() {
        return new StatusResponse(
                "gigahack26-api",
                probePostgres(),
                probeMongo(),
                clock.instant(),
                count(companies::count),
                count(evidence::count),
                count(signals::count),
                count(questions::count),
                captures.size(),
                board.primaryEngine(),
                board.languageModelAvailable(),
                board.degraded());
    }

    private String probePostgres() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return "up";
        } catch (RuntimeException e) {
            return "down";
        }
    }

    private String probeMongo() {
        try {
            mongo.getDb().runCommand(new Document("ping", 1));
            return "up";
        } catch (RuntimeException e) {
            return "down";
        }
    }

    private long count(java.util.function.Supplier<Long> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
