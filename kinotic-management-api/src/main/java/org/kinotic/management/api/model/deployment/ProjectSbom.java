package org.kinotic.management.api.model.deployment;

import java.util.Date;

/**
 * The software bill of materials of a project: every package its {@code bun.lock} resolves, as a
 * CycloneDX document kept in the organization's storage. A deployment replaces it when the
 * project's dependencies changed.
 *
 * @param commitSha      full 40-character SHA of the commit whose checkout the document was
 *                       generated from; later commits that leave the dependencies unchanged keep
 *                       the document
 * @param dependencyHash the {@link ProjectArtifacts#dependencyHash()} the document was generated
 *                       from: the SBOM is current for a commit whose artifacts carry the same hash
 * @param componentCount how many components the document lists
 * @param generated      when the document was generated
 */
public record ProjectSbom(String commitSha, String dependencyHash, int componentCount, Date generated) {}
