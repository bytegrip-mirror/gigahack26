package com.pistonpunk.gigahack26.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ControllerToolInvoker {

    private static final String RESULT_KEY_STATUS = "status";

    private final ObjectMapper mapper;
    private final Validator validator;

    public ControllerToolInvoker(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public CallToolResult call(McpToolDeclaration tool, Map<String, Object> arguments) {
        Map<String, Object> given = arguments == null ? Map.of() : arguments;
        try {
            Object[] values = tool.parameters().stream()
                    .map(parameter -> value(parameter, given))
                    .toArray();
            return result(tool.method().invoke(tool.controller(), values));
        } catch (McpToolArgumentException e) {
            return error(e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable cause = Optional.ofNullable(e.getCause()).orElse(e);
            return error(tool.name() + " failed: " + cause.getMessage());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("The controller method behind " + tool.name() + " is not accessible", e);
        }
    }

    private Object value(McpToolParameter parameter, Map<String, Object> arguments) {
        Object raw = Optional.ofNullable(arguments.get(parameter.name()))
                .or(parameter::fallback)
                .orElse(null);
        if (raw == null) {
            if (parameter.required()) {
                throw new McpToolArgumentException("Missing required argument \"" + parameter.name() + "\"");
            }
            return null;
        }
        Object converted = convert(parameter, raw);
        if (parameter.validated()) {
            validate(parameter, converted);
        }
        return converted;
    }

    private Object convert(McpToolParameter parameter, Object raw) {
        try {
            return mapper.convertValue(raw, mapper.constructType(parameter.type()));
        } catch (JacksonException | IllegalArgumentException e) {
            throw new McpToolArgumentException("Argument \"" + parameter.name() + "\" is not a valid "
                    + parameter.type().getTypeName() + ": " + e.getMessage(), e);
        }
    }

    private void validate(McpToolParameter parameter, Object value) {
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String reasons = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new McpToolArgumentException("Argument \"" + parameter.name() + "\" is invalid: " + reasons);
        }
    }

    private CallToolResult result(Object returned) {
        if (returned instanceof ResponseEntity<?> response) {
            Object body = response.hasBody()
                    ? response.getBody()
                    : Map.of(RESULT_KEY_STATUS, response.getStatusCode().value());
            return CallToolResult.builder()
                    .addTextContent(mapper.writeValueAsString(body))
                    .isError(response.getStatusCode().isError())
                    .build();
        }
        return CallToolResult.builder()
                .addTextContent(mapper.writeValueAsString(returned))
                .isError(false)
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .build();
    }
}
