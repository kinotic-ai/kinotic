package org.kinotic.gateway.internal.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;

/**
 * One tool as served by {@code tools/list}: name, description, the input schema object, and behavior hints.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class McpToolListing {

    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String title;

    private String description;

    private JsonNode inputSchema;

    private McpToolAnnotations annotations;
}
