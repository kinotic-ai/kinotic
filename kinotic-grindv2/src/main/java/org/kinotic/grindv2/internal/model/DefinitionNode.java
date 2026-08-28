package org.kinotic.grindv2.internal.model;

import org.kinotic.grindv2.internal.api.model.DefaultJobDefinition;

/**
 * A task that executes a nested {@link DefaultJobDefinition}.
 *
 * @param sequence   the node's position within its parent definition
 * @param definition the nested definition to execute
 */
public record DefinitionNode(int sequence, DefaultJobDefinition definition) implements JobNode {

    @Override
    public String description() {
        return definition.getDescription();
    }

}
