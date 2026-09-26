package com.pistonpunk.gigahack26.mcp;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

public record McpToolParameter(String name, McpParameterSource source, Type type, boolean required,
                               String defaultValue, boolean validated, Map<String, Object> schema) {

    public Optional<String> fallback() {
        return Optional.ofNullable(defaultValue);
    }
}
