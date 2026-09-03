package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Organization;

/**
 * Prepares what a newly created organization needs beyond its record. Every implementation
 * is called once with the saved organization, right after it is created, and completes once
 * its work is under way: work that takes long, such as provisioning cloud resources,
 * continues in the background and records its outcome on the organization rather than
 * holding up creation. A failure is logged and never fails the creation.
 */
public interface OrganizationProvisioner {

    /**
     * Starts preparing the organization.
     *
     * @param organization the organization, as saved
     * @return a future completing once the work is under way
     */
    Future<Void> provision(Organization organization);

}
