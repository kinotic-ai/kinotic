package org.kinotic.system.api.model.deployment;

import org.kinotic.management.api.model.UiDeployment;

import java.util.List;

/**
 * The UI deployments a deployment run left the project with: every one of the project's,
 * the ones the run published and the ones it orphaned alike, ordered by name.
 *
 * @param deployments the project's UI deployments after the run
 */
public record UiDeployments(List<UiDeployment> deployments) {}
