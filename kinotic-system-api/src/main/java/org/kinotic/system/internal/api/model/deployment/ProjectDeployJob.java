package org.kinotic.system.internal.api.model.deployment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.system.api.model.grind.JobDefinition;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A single-use deploy job: the assembled {@link JobDefinition} plus what its tasks resolve
 * while running, so the caller can record how far a run got whatever its outcome.
 */
@RequiredArgsConstructor
public class ProjectDeployJob {

    @Getter
    private final JobDefinition definition;

    private final AtomicReference<DeployTarget> target;

    private final AtomicReference<String> runtimeWorkloadId;

    /**
     * @return the resolved deployment target, or {@code null} while the run has not
     *         resolved one
     */
    public DeployTarget getTarget() {
        return target.get();
    }

    /**
     * @return the id of the runtime workload serving the deployment, or {@code null} while
     *         the run has not ensured one
     */
    public String getRuntimeWorkloadId() {
        return runtimeWorkloadId.get();
    }

}
