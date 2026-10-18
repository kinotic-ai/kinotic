package org.kinotic.domain.api.services;

import io.vertx.core.Future;

/**
 * Provisions what the edge needs to serve an organization's sites under its own subdomain of the sites
 * domain, {@code <app>--<ui>.<org>.<sites domain>}: the record the subdomain resolves by, from which the
 * edge's certificate for it follows. Called when an organization is created and again before its first
 * site is published, so an organization whose record could not be created at sign-up is provisioned
 * the moment it needs serving.
 */
public interface SiteDomainProvisioner {

    /**
     * Ensures the organization's subdomain is provisioned, creating its record when there is none and
     * leaving an existing one as it is.
     *
     * @param organizationId the organization's id, which is its subdomain's label
     * @return completes once the subdomain resolves at the edge, or fails when the edge refused
     */
    Future<Void> ensure(String organizationId);
}
