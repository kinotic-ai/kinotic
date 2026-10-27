package org.kinotic.system.internal.api.services;

import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.system.api.services.SystemJobService;

/**
 * A grind job system users can start on demand through {@link SystemJobService}. Every
 * Spring bean implementing this interface is listed, and each start runs a fresh definition on
 * behalf of the system. The console shows each task with the icon and the views it declares; a
 * step that runs a workload shows its log by declaring it as the step's start view.
 */
public interface SystemJob {

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set; the name identifies the job and must be
     *         unique among the system jobs
     */
    JobDefinition createJobDefinition();

}
