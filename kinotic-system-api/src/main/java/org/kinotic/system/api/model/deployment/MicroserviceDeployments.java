package org.kinotic.system.api.model.deployment;

import org.kinotic.management.api.model.MicroserviceDeployment;

import java.util.List;

/**
 * The microservice deployments a deployment run left the project with: every one of the
 * project's, the ones the run ensured and the ones it orphaned alike, ordered by name.
 *
 * @param deployments the project's microservice deployments after the run
 */
public record MicroserviceDeployments(List<MicroserviceDeployment> deployments) {}
