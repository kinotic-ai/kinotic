package org.kinotic.idl.internal.directory;

import org.apache.commons.lang3.StringUtils;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.annotations.McpToolInfo;

import java.util.Locale;
import java.util.Set;

/**
 * The MCP tool metadata one declaration states for a function. An empty {@link #title} or
 * {@link #description}, and a hint set with nothing set, mean the declaration states nothing there and the
 * next declaration decides.
 */
record McpToolMetadata(String title,
                       String description,
                       boolean readOnlyHint,
                       boolean destructiveHint,
                       boolean idempotentHint) {

    /**
     * Metadata stating nothing at all.
     */
    static final McpToolMetadata NONE = new McpToolMetadata("", "", false, false, false);

    private static final Set<String> READ_ONLY_VERBS = Set.of("count", "exists", "fetch", "find", "get",
                                                              "has", "is", "list", "load", "query", "read",
                                                              "search");

    // a verb that replaces or removes state: not an additive update, and repeating it lands on the same state
    private static final Set<String> DESTRUCTIVE_VERBS = Set.of("clear", "delete", "destroy", "drop", "purge",
                                                                "put", "remove", "save", "set", "truncate",
                                                                "update", "upsert");

    static McpToolMetadata from(McpTool annotation) {
        return new McpToolMetadata(annotation.title(),
                                   annotation.description(),
                                   annotation.readOnlyHint(),
                                   annotation.destructiveHint(),
                                   annotation.idempotentHint());
    }

    static McpToolMetadata from(McpToolInfo annotation) {
        return new McpToolMetadata(annotation.title(),
                                   annotation.description(),
                                   annotation.readOnlyHint(),
                                   annotation.destructiveHint(),
                                   annotation.idempotentHint());
    }

    /**
     * Derives the hints a function name implies: a reading verb is read-only, a verb that replaces or removes
     * state is destructive and idempotent, and a create that yields to an existing entity is idempotent. A
     * name implying none of those states no hint.
     *
     * @param functionName the function name to read
     * @return the hints the name implies, carrying no title or description
     */
    static McpToolMetadata fromFunctionName(String functionName) {
        String verb = StringUtils.splitByCharacterTypeCamelCase(functionName)[0].toLowerCase(Locale.ROOT);
        McpToolMetadata ret;
        if (READ_ONLY_VERBS.contains(verb)) {
            ret = hints(true, false, false);
        } else if (DESTRUCTIVE_VERBS.contains(verb)) {
            ret = hints(false, true, true);
        } else if (functionName.endsWith("IfNotExist") || functionName.endsWith("IfNotExists")) {
            ret = hints(false, false, true);
        } else {
            ret = NONE;
        }
        return ret;
    }

    /**
     * Whether this declaration states the tool's behavior hints.
     */
    boolean declaresHints() {
        return readOnlyHint || destructiveHint || idempotentHint;
    }

    private static McpToolMetadata hints(boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint) {
        return new McpToolMetadata("", "", readOnlyHint, destructiveHint, idempotentHint);
    }

}
