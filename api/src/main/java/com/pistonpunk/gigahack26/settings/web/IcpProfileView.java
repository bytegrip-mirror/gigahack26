package com.pistonpunk.gigahack26.settings.web;

import java.util.List;
import java.util.UUID;

public record IcpProfileView(
        UUID id,
        String name,
        List<String> countries,
        List<String> industries,
        int minEmployees,
        boolean active,
        int sortOrder,
        long companies) {
}
