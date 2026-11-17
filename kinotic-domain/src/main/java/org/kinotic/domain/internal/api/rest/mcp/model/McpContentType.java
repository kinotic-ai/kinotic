package org.kinotic.domain.internal.api.rest.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The MCP content item types, serialized with the spec's lowercase wire values.
 */
@Getter
@RequiredArgsConstructor
public enum McpContentType {

    TEXT("text"),
    IMAGE("image"),
    AUDIO("audio"),
    RESOURCE_LINK("resource_link"),
    RESOURCE("resource");

    @JsonValue
    private final String wireValue;
}
