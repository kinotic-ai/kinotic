package org.kinotic.grindv2.api.model;

/**
 * The scope a nested {@link JobDefinition} executes in, governing where the values its steps
 * store remain visible.
 */
public enum JobScope {

    /**
     * The nested definition uses its parent's scope, so values it stores remain available to
     * the parent's later steps after it completes.
     */
    PARENT,

    /**
     * The nested definition creates a child scope, so values it stores are discarded when it
     * completes.
     */
    CHILD

}
