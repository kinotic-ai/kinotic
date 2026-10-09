package org.kinotic.system.api.services;

import org.kinotic.grind.api.model.JobDefinition;

/**
 * A grind job the platform's operators can start on demand through {@link SystemJobService}. Every
 * Spring bean implementing this interface is listed, and each start runs a fresh definition on
 * behalf of the platform.
 */
public interface SystemJob {

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set; the name identifies the job and must be
     *         unique among the platform's system jobs
     */
    JobDefinition createJobDefinition();

}
