package org.kinotic.grindv2.internal.model;

/**
 * One node of a {@link DefaultJobDefinition}'s task tree, executed by the
 * {@link JobInterpreter}.
 */
public sealed interface JobNode permits TaskNode, DefinitionNode {

    /**
     * The 1-based position of this node within its parent definition.
     * @return the sequence number
     */
    int sequence();

    /**
     * The description shown in run records.
     * @return the description
     */
    String description();

}
