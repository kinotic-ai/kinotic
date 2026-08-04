

package org.kinotic.orchestrator.api.grind;

/**
 * The "Scope" that a {@link JobDefinition} should be executed in.
 * This affects where the {@link Task} results from the job will be stored if required
 *
 *
 * Created by Navid Mitchell on 8/5/20
 */
public enum JobScope {

    /**
     * The {@link JobDefinition} will use the scope of the parent {@link JobDefinition},
     * so results it stores remain available to the caller after it completes
     */
    PARENT,

    /**
     * The {@link JobDefinition} will create a new scope that is the child of the parent scope,
     * so results it stores are discarded when it completes
     */
    CHILD

}
