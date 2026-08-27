package org.kinotic.grindv2.internal;

/**
 * A step that executes a nested {@link DefaultJobDefinition}.
 *
 * @param sequence   the node's position within its parent definition
 * @param definition the nested definition to execute
 */
public record DefinitionNode(int sequence, DefaultJobDefinition definition) implements StepNode {

    @Override
    public String description() {
        return definition.getDescription();
    }

}
