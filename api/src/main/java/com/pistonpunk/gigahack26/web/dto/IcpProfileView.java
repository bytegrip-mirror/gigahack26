package com.pistonpunk.gigahack26.web.dto;

import java.util.List;
import java.util.UUID;

public record IcpProfileView(
        UUID id,
        String name,
        List<String> countries,
        List<String> regions,
        List<String> industries,
        int minEmployees,
        List<String> sizeBands,
        boolean excludeCompetitors,
        boolean untouchedOnly,
        boolean active,
        int sortOrder,
        long companies) {
}
