package com.pistonpunk.gigahack26.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmissionRequest(
        @NotBlank @Size(max = 80) String author,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 4000) String body,
        @Size(max = 8) List<@Size(max = 24) String> tags) {

    public SubmissionRequest {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
