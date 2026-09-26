package com.pistonpunk.gigahack26.mcp;

import java.lang.reflect.Method;
import java.util.List;

public record McpToolDeclaration(String name, String description, String httpMethod, String path,
                                 Object controller, Method method, List<McpToolParameter> parameters) {
}
