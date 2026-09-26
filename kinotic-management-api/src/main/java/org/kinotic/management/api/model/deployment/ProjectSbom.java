package org.kinotic.management.api.model.deployment;

import java.util.Date;

/**
 * The software bill of materials of the dependencies a project's {@link ProjectArtifacts} list:
 * every package the {@code bun.lock} of their checkout resolves, as a CycloneDX document kept in
 * the organization's storage. A sync that reports other dependencies drops it, and the deployment
 * generates it again.
 *
 * @param componentCount how many components the document lists
 * @param generated      when the document was generated
 */
public record ProjectSbom(int componentCount, Date generated) {}
