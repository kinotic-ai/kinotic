package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Organization;

/**
 * Prepares what an organization needs beyond its record. Every implementation is called by
 * {@link OrganizationService#provision} with the organization, once when it is created and
 * again whenever an operator asks, so its work must be idempotent. It completes once its
 * work is under way: work that takes long, such as provisioning cloud resources, continues
 * in the background and records its outcome on the organization rather than holding up
 * the caller. A failure to start is logged and never fails the caller.
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
