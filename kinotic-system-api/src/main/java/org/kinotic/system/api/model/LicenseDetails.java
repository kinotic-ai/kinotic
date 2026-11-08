package org.kinotic.system.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.kinotic.domain.internal.api.ee.license.EnterpriseFeature;
import org.kinotic.domain.internal.api.ee.license.LicenseStatus;

import java.util.Date;
import java.util.Set;

/**
 * The Kinotic Enterprise License the cluster runs on, as the system console shows it.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class LicenseDetails {

    /** The party the license is issued to. */
    private String licensee;

    /** When the license was issued, or {@code null} when its key does not say. */
    private Date issuedAt;

    /** When the license expires. */
    private Date expiresAt;

    /** When the license stops turning its features on, a grace period after it expires. */
    private Date graceEndsAt;

    /** Where the license stands now. */
    private LicenseStatus status;

    /** The Enterprise Features the license turns on while it is active or in its grace period. */
    private Set<EnterpriseFeature> features;
}
