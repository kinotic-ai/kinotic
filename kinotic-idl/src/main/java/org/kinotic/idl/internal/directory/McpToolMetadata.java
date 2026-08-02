package org.kinotic.idl.internal.directory;

import org.apache.commons.lang3.StringUtils;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.idl.api.annotations.McpToolInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

    // a verb that changes state additively: no hint of its own, but it disqualifies a reading verb elsewhere
    // in the same name
    private static final Set<String> ADDITIVE_VERBS = Set.of("add", "apply", "create", "execute", "generate",
                                                             "import", "insert", "invoke", "publish",
                                                             "register", "retry", "run", "send", "start",
                                                             "stop", "submit", "trigger", "upload");

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
     * Derives the hints a function name implies: a name naming a verb that replaces or removes state is
     * destructive and idempotent, a create that yields to an existing entity is idempotent, and a name whose
     * only verbs read is read-only. Any other name implies no hint.
     *
     * @param functionName the function name to read
     * @return the hints the name implies, carrying no title or description
     */
    static McpToolMetadata fromFunctionName(String functionName) {
        // every word counts, not just the leading one, so peopleCount reads as a count; and a mutating word
        // anywhere outranks a reading one, so getOrCreatePerson is never served as read-only — serving a
        // mutation as safe to call unattended is the one mistake worth ordering the rules around
        Set<String> words = words(functionName);
        McpToolMetadata ret;
        if (!Collections.disjoint(words, DESTRUCTIVE_VERBS)) {
            ret = hints(false, true, true);
        } else if (functionName.endsWith("IfNotExist") || functionName.endsWith("IfNotExists")) {
            ret = hints(false, false, true);
        } else if (!Collections.disjoint(words, ADDITIVE_VERBS)) {
            ret = NONE;
        } else if (!Collections.disjoint(words, READ_ONLY_VERBS)) {
            ret = hints(true, false, false);
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

    private static Set<String> words(String functionName) {
        return Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(functionName))
                     .map(word -> word.toLowerCase(Locale.ROOT))
                     .collect(Collectors.toSet());
    }

}
