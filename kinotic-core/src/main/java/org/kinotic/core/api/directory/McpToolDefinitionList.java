package org.kinotic.core.api.directory;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * One page of callable MCP tools, shaped as a {@code tools/list} result: the tools plus a cursor when more
 * results exist.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolDefinitionList {

    private List<McpToolDefinition> tools;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nextCursor;
}
