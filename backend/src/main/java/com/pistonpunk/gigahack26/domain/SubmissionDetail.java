package com.pistonpunk.gigahack26.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "submission_details")
public class SubmissionDetail {

    @Id
    private String id;

    @Indexed(unique = true)
    private UUID submissionId;

    private String body;

    private List<String> tags;

    private Instant createdAt;

    protected SubmissionDetail() {
    }

    public SubmissionDetail(UUID submissionId, String body, List<String> tags, Instant createdAt) {
        this.submissionId = submissionId;
        this.body = body;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getBody() {
        return body;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
