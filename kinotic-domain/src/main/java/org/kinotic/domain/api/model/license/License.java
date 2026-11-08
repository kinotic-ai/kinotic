package org.kinotic.domain.api.model.license;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.Set;

/**
 * A verified Kinotic Enterprise License: who it is issued to, when it was issued and expires, and the Enterprise
 * Features it turns on until its grace period ends.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class License {

    /** The party the license is issued to. */
    private String licensee;

    /** When the license was issued, or {@code null} when its key does not say. */
    private Date issuedAt;

    /** When the license expires. */
    private Date expiresAt;

    /** When the license stops turning its features on, a grace period after it expires. */
    private Date graceEndsAt;

    /** Where the license stands at the time it was handed out. */
    private LicenseStatus status;

    /** The Enterprise Features the license turns on while it is active or in its grace period. */
    private Set<EnterpriseFeature> features;
}
