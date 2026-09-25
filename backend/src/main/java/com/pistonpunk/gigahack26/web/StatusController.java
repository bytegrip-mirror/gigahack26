package com.pistonpunk.gigahack26.web;

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
    private final Clock clock;

    public StatusController(JdbcTemplate jdbc, MongoTemplate mongo, Clock clock) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.clock = clock;
    }

    @GetMapping
    public StatusResponse status() {
        return new StatusResponse("gigahack26-api", probePostgres(), probeMongo(), clock.instant());
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
}
