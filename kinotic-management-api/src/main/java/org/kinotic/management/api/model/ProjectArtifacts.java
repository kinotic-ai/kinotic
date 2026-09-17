package org.kinotic.management.api.model;

import java.util.List;

/**
 * The artifacts one commit of a project contains, as the sync workload found them in the
 * checkout: what a deployment of that commit runs and publishes. Both lists are ordered by
 * name.
 *
 * @param microservices the microservice artifacts, empty when the commit has none
 * @param uis           the UI artifacts, empty when the commit has none
 */
public record ProjectArtifacts(List<MicroserviceArtifact> microservices, List<UiArtifact> uis) {}
