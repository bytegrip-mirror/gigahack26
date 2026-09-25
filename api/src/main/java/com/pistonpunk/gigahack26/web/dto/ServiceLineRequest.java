package com.pistonpunk.gigahack26.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceLineRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 600) String description,
        @Size(max = 600) String valueProposition,
        int sortOrder,
        boolean active) {
}
