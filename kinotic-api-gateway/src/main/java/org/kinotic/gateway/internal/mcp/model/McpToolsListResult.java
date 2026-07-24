package org.kinotic.gateway.internal.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The {@code tools/list} result: the caller's page of tools, with a cursor when more results exist.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolsListResult {

    private List<McpToolListing> tools;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nextCursor;
}
