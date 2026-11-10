package org.kinotic.domain.api.model.license;

/**
 * Where a {@link License} stands at a given time.
 */
public enum LicenseStatus {
    /** Before its expiry: its features are on. */
    ACTIVE,
    /** Expired, before its grace period ends: its features stay on. */
    GRACE_PERIOD,
    /** Its grace period has ended: its features are off. */
    EXPIRED
}
