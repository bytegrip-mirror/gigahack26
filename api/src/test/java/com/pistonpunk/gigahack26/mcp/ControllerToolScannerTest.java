package com.pistonpunk.gigahack26.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerToolScannerTest {

    private static final UUID COMPANY_ID = UUID.fromString("7b0c6c52-2f7a-4b43-9d8e-0f3f2d7e9a11");

    @RestController
    @RequestMapping("/api/sample-leads")
    static class SampleLeadController {

        record NoteRequest(@NotBlank String note) {
        }

        @GetMapping("/{companyId}")
        public Map<String, Object> companyDetail(@PathVariable UUID companyId,
                                                 @RequestParam(name = "serviceLine", required = false) String line,
                                                 @RequestParam(name = "limit", defaultValue = "5") int limit) {
            return Map.of("companyId", companyId, "serviceLine", String.valueOf(line), "limit", limit);
        }

        @PostMapping("/{companyId}/notes")
        public ResponseEntity<Void> addNote(@PathVariable UUID companyId, @Valid @RequestBody NoteRequest request) {
            return ResponseEntity.notFound().build();
        }

        @GetMapping("/raw")
        public String raw(HttpServletRequest request) {
            return "";
        }

        public String helper() {
            return "";
        }
    }

    @RestController
    @RequestMapping("/api/admin")
    static class SampleAdminController {

        @PostMapping("/build")
        public String build() {
            return "";
        }
    }

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final ControllerToolScanner scanner = new ControllerToolScanner(mapper);
    private final ControllerToolInvoker invoker = new ControllerToolInvoker(mapper,
            Validation.buildDefaultValidatorFactory().getValidator());

    private Map<String, McpToolDeclaration> scan() {
        return scanner.scan(List.of(new SampleLeadController(), new SampleAdminController()));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    @Test
    void everyMappedPublicMethodBecomesASnakeCaseToolPrefixedByItsController() {
        assertEquals(Set.of("sample_lead_company_detail", "sample_lead_add_note"), scan().keySet());
    }

    @Test
    void theAdminPathAndMethodsWithUnmappedParametersAreNotExposed() {
        Map<String, McpToolDeclaration> tools = scan();

        assertFalse(tools.containsKey("sample_admin_build"));
        assertFalse(tools.containsKey("sample_lead_raw"));
        assertFalse(tools.containsKey("sample_lead_helper"));
    }

    @Test
    void parametersCarryTheirSourceTypeAndWhetherTheyAreRequired() {
        McpToolDeclaration tool = scan().get("sample_lead_company_detail");
        Map<String, McpToolParameter> parameters = Map.of(
                "companyId", tool.parameters().get(0),
                "serviceLine", tool.parameters().get(1),
                "limit", tool.parameters().get(2));

        assertEquals("GET", tool.httpMethod());
        assertEquals("/api/sample-leads/{companyId}", tool.path());
        assertEquals(McpParameterSource.PATH, parameters.get("companyId").source());
        assertEquals(UUID.class, parameters.get("companyId").type());
        assertTrue(parameters.get("companyId").required());
        assertEquals(McpParameterSource.QUERY, parameters.get("serviceLine").source());
        assertFalse(parameters.get("serviceLine").required());
        assertFalse(parameters.get("limit").required());
        assertEquals("5", parameters.get("limit").defaultValue());
        assertEquals("integer", parameters.get("limit").schema().get("type"));
        assertEquals("5", parameters.get("limit").schema().get("default"));
    }

    @Test
    void theBodyIsOneArgumentNamedBodyAndIsValidatedWhenTheControllerAsksForIt() {
        McpToolParameter body = scan().get("sample_lead_add_note").parameters().get(1);

        assertEquals("body", body.name());
        assertEquals(McpParameterSource.BODY, body.source());
        assertTrue(body.validated());
        assertEquals("object", body.schema().get("type"));
        assertFalse(body.schema().containsKey("required"));
    }

    @Test
    void aCallConvertsTheArgumentsAndAppliesTheQueryDefault() {
        CallToolResult result = invoker.call(scan().get("sample_lead_company_detail"),
                Map.of("companyId", COMPANY_ID.toString()));

        assertFalse(result.isError());
        Map<?, ?> body = mapper.readValue(text(result), Map.class);
        assertEquals(COMPANY_ID.toString(), body.get("companyId"));
        assertEquals("null", body.get("serviceLine"));
        assertEquals(5, body.get("limit"));
    }

    @Test
    void aMissingRequiredArgumentIsAToolErrorNotAnException() {
        CallToolResult result = invoker.call(scan().get("sample_lead_company_detail"), Map.of());

        assertTrue(result.isError());
        assertEquals("Missing required argument \"companyId\"", text(result));
    }

    @Test
    void anInvalidBodyIsRefusedBeforeTheControllerRuns() {
        CallToolResult result = invoker.call(scan().get("sample_lead_add_note"),
                Map.of("companyId", COMPANY_ID.toString(), "body", Map.of("note", "")));

        assertTrue(result.isError());
        assertTrue(text(result).startsWith("Argument \"body\" is invalid: note"));
    }

    @Test
    void anErrorStatusFromTheControllerIsReportedAsAToolError() {
        CallToolResult result = invoker.call(scan().get("sample_lead_add_note"),
                Map.of("companyId", COMPANY_ID.toString(), "body", Map.of("note", "called twice")));

        assertTrue(result.isError());
        assertEquals("{\"status\":404}", text(result));
    }

    @Test
    void camelCaseNamesFoldToSnakeCase() {
        assertEquals("push_to_hubspot", ControllerToolScanner.snakeCase("pushToHubspot"));
        assertEquals("icp_profile", ControllerToolScanner.snakeCase("IcpProfile"));
    }
}
