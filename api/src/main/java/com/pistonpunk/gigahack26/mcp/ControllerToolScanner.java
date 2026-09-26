package com.pistonpunk.gigahack26.mcp;

import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ControllerToolScanner {

    private static final String PATH_PREFIX_EXCLUDED = "/api/admin";
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String BODY_PARAMETER_NAME = "body";
    private static final String SCHEMA_KEY_DIALECT = "$schema";
    private static final String SCHEMA_KEY_DEFAULT = "default";
    private static final String SCHEMA_KEY_REQUIRED = "required";
    private static final String HTTP_METHOD_ANY = "ANY";
    private static final TypeReference<Map<String, Object>> SCHEMA_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public ControllerToolScanner(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, McpToolDeclaration> scan(Collection<Object> controllers) {
        return controllers.stream()
                .flatMap(this::declarations)
                .collect(Collectors.toMap(McpToolDeclaration::name, Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Two controller methods map to the MCP tool " + first.name()
                            + ": " + first.method() + " and " + second.method());
                }, TreeMap::new));
    }

    private Stream<McpToolDeclaration> declarations(Object controller) {
        Class<?> type = AopUtils.getTargetClass(controller);
        String prefix = Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class))
                .map(mapping -> firstPath(mapping.path()))
                .orElse("");
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .flatMap(method -> declaration(controller, type, prefix, method).stream());
    }

    private Optional<McpToolDeclaration> declaration(Object controller, Class<?> type, String prefix, Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return Optional.empty();
        }
        String path = prefix + firstPath(mapping.path());
        if (path.startsWith(PATH_PREFIX_EXCLUDED)) {
            return Optional.empty();
        }
        List<Optional<McpToolParameter>> parameters = Arrays.stream(method.getParameters())
                .map(this::parameter)
                .toList();
        if (parameters.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        String httpMethod = mapping.method().length == 0
                ? HTTP_METHOD_ANY
                : Arrays.stream(mapping.method()).map(Enum::name).collect(Collectors.joining(","));
        String name = snakeCase(controllerName(type)) + "_" + snakeCase(method.getName());
        String description = "Calls " + httpMethod + " " + path + " (" + type.getSimpleName() + "."
                + method.getName() + ") and returns its JSON response.";
        return Optional.of(new McpToolDeclaration(name, description, httpMethod, path, controller, method,
                parameters.stream().map(Optional::orElseThrow).toList()));
    }

    private Optional<McpToolParameter> parameter(Parameter parameter) {
        Type type = parameter.getParameterizedType();
        boolean validated = parameter.isAnnotationPresent(Valid.class) || parameter.isAnnotationPresent(Validated.class);

        PathVariable path = AnnotatedElementUtils.findMergedAnnotation(parameter, PathVariable.class);
        if (path != null) {
            return Optional.of(new McpToolParameter(name(path.name(), parameter), McpParameterSource.PATH, type,
                    path.required(), null, validated, schema(type)));
        }
        RequestParam query = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestParam.class);
        if (query != null) {
            boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(query.defaultValue());
            Map<String, Object> schema = schema(type);
            if (hasDefault) {
                schema.put(SCHEMA_KEY_DEFAULT, query.defaultValue());
            }
            return Optional.of(new McpToolParameter(name(query.name(), parameter), McpParameterSource.QUERY, type,
                    query.required() && !hasDefault, hasDefault ? query.defaultValue() : null, validated, schema));
        }
        RequestBody body = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestBody.class);
        if (body != null) {
            return Optional.of(new McpToolParameter(BODY_PARAMETER_NAME, McpParameterSource.BODY, type,
                    body.required(), null, validated, withoutRequiredFields(schema(type))));
        }
        return Optional.empty();
    }

    private static String name(String declared, Parameter parameter) {
        if (!declared.isEmpty()) {
            return declared;
        }
        if (!parameter.isNamePresent()) {
            throw new IllegalStateException("Compile with -parameters, or name " + parameter
                    + " in its mapping annotation");
        }
        return parameter.getName();
    }

    private Map<String, Object> schema(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>(
                mapper.readValue(JsonSchemaGenerator.generateForType(type), SCHEMA_TYPE));
        schema.remove(SCHEMA_KEY_DIALECT);
        return schema;
    }

    private static Map<String, Object> withoutRequiredFields(Map<String, Object> schema) {
        return schema.entrySet().stream()
                .filter(entry -> !(SCHEMA_KEY_REQUIRED.equals(entry.getKey()) && entry.getValue() instanceof List))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> withoutRequiredFields(entry.getValue()),
                        (first, second) -> first, LinkedHashMap::new));
    }

    @SuppressWarnings("unchecked")
    private static Object withoutRequiredFields(Object node) {
        if (node instanceof Map<?, ?> map) {
            return withoutRequiredFields((Map<String, Object>) map);
        }
        if (node instanceof List<?> list) {
            return list.stream().map(ControllerToolScanner::withoutRequiredFields).toList();
        }
        return node;
    }

    private static String firstPath(String[] paths) {
        return paths.length == 0 ? "" : paths[0];
    }

    private static String controllerName(Class<?> type) {
        String simpleName = type.getSimpleName();
        return simpleName.endsWith(CONTROLLER_SUFFIX)
                ? simpleName.substring(0, simpleName.length() - CONTROLLER_SUFFIX.length())
                : simpleName;
    }

    static String snakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase();
    }
}
