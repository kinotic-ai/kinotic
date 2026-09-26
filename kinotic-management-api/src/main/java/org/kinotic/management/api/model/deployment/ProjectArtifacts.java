package org.kinotic.management.api.model.deployment;

import java.util.List;

/**
 * The artifacts one commit of a project contains, as the sync workload found them in the
 * checkout: what a deployment of that commit runs and publishes, and a fingerprint of the
 * dependencies installed for them. Both lists are ordered by name.
 *
 * @param commitSha      full 40-character SHA of the commit the artifacts were found in
 * @param microservices  the microservice artifacts, empty when the commit has none
 * @param uis            the UI artifacts, empty when the commit has none
 * @param dependencyHash a fingerprint of the dependencies the checkout installed: a SHA-256 of its
 *                       {@code bun.lock} and of the SBOM generator's version, so two commits with
 *                       the same fingerprint have the same {@link ProjectSbom}. {@code null} when
 *                       the checkout has no {@code bun.lock}
 */
public record ProjectArtifacts(String commitSha,
                               List<MicroserviceArtifact> microservices,
                               List<UiArtifact> uis,
                               String dependencyHash) {}
