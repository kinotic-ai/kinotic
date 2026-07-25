package org.kinotic.domain.internal.api.rest.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The {@code tools/call} result: the reply content, with {@code isError} true for a tool-level failure.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpCallToolResult {

    private List<McpContent> content;

    // MCP names the wire property "isError"; the bean property is "error"
    @JsonProperty("isError")
    private boolean error;

    public static McpCallToolResult text(String text) {
        return result(text, false);
    }

    public static McpCallToolResult error(String message) {
        return result(message, true);
    }

    private static McpCallToolResult result(String text, boolean error) {
        return new McpCallToolResult().setContent(List.of(new McpContent().setType(McpContentType.TEXT).setText(text)))
                                      .setError(error);
    }
}
