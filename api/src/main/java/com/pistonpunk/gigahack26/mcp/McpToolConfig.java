package com.pistonpunk.gigahack26.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class McpToolConfig {

    private static final String SCHEMA_TYPE_OBJECT = "object";

    @Bean
    public List<SyncToolSpecification> controllerTools(ApplicationContext context, ControllerToolScanner scanner,
                                                       ControllerToolInvoker invoker) {
        Map<String, McpToolDeclaration> declarations =
                scanner.scan(context.getBeansWithAnnotation(RestController.class).values());
        return declarations.values().stream()
                .map(declaration -> specification(declaration, invoker))
                .toList();
    }

    private static SyncToolSpecification specification(McpToolDeclaration declaration, ControllerToolInvoker invoker) {
        Tool tool = Tool.builder()
                .name(declaration.name())
                .description(declaration.description())
                .inputSchema(inputSchema(declaration))
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> invoker.call(declaration, request.arguments()))
                .build();
    }

    private static JsonSchema inputSchema(McpToolDeclaration declaration) {
        Map<String, Object> properties = declaration.parameters().stream()
                .collect(Collectors.toMap(McpToolParameter::name, McpToolParameter::schema,
                        (first, second) -> first, LinkedHashMap::new));
        List<String> required = declaration.parameters().stream()
                .filter(McpToolParameter::required)
                .map(McpToolParameter::name)
                .toList();
        return new JsonSchema(SCHEMA_TYPE_OBJECT, properties, required, false, null, null);
    }
}
