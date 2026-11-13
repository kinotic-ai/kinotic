package org.kinotic.management.api.services.security;

import org.kinotic.grind.api.model.JobDefinition;

/**
 * A grind job that places stored records in the authorization graph. A server started with
 * {@code kinotic.authz.seedGraph} runs every Spring bean implementing this interface once it is up, on behalf of
 * the system and as an exclusive job, so of the servers starting together one runs it. The records are ones a
 * migration or a script wrote instead of the services, which place what they create. A job places only what is
 * not yet in the graph, so a run that follows a completed one finds nothing to place.
 */
public interface AuthzGraphSeedJob {

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set
     */
    JobDefinition createJobDefinition();

}
