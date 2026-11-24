package org.kinotic.system.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.vulnerability.PackageEcosystem;
import org.kinotic.management.api.model.vulnerability.VulnerabilityAdvisory;

import java.util.Date;

/**
 * How far the advisories of one {@link PackageEcosystem} have been read from OSV, and how many of
 * them are stored.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OsvImportStatus {

    private PackageEcosystem ecosystem;

    /**
     * The newest {@link VulnerabilityAdvisory#getModified()} read; every advisory OSV changed up to
     * it is stored as OSV holds it. {@code null} until the ecosystem's first import completes.
     */
    private Date syncedThrough;

    /**
     * How many advisories naming a package of the ecosystem are stored and not withdrawn.
     */
    private long advisoryCount;

}
