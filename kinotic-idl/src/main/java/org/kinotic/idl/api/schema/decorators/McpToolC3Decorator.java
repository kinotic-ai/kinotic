package org.kinotic.idl.api.schema.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Marks a function as a Model Context Protocol tool.
 * A function carrying this decorator is exposed to LLM callers as an MCP tool, using {@link #description} as the
 * tool's LLM-facing contract and the three hints as MCP tool behavior annotations.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class McpToolC3Decorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "McpTool";

    /**
     * The explicit tool name, or null when the name is derived from the service and function names.
     */
    private String name;

    /**
     * The LLM-facing description of what the tool does.
     */
    private String description;

    /**
     * Indicates the tool does not modify its environment.
     */
    private boolean readOnlyHint = false;

    /**
     * Indicates the tool may perform destructive updates (only meaningful when not read-only).
     */
    private boolean destructiveHint = false;

    /**
     * Indicates repeated calls with the same arguments have no additional effect.
     */
    private boolean idempotentHint = false;

    public McpToolC3Decorator() {
        this.targets = List.of(DecoratorTarget.FUNCTION);
    }

}
