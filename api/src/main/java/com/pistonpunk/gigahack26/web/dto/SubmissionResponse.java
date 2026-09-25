package com.pistonpunk.gigahack26.web.dto;

import com.pistonpunk.gigahack26.domain.Submission;
import com.pistonpunk.gigahack26.domain.SubmissionDetail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        String author,
        String title,
        String body,
        List<String> tags,
        Instant createdAt) {

    public static SubmissionResponse of(Submission submission, SubmissionDetail detail) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getAuthor(),
                submission.getTitle(),
                detail == null ? null : detail.getBody(),
                detail == null ? List.of() : detail.getTags(),
                submission.getCreatedAt());
    }
}
