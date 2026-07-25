package org.kinotic.gateway.internal.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

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

    // the schema is minted as a JSON string at registration; raw embedding serves it as an object without a parse
    @JsonRawValue
    private String inputSchema;

    private McpToolAnnotations annotations;
}
