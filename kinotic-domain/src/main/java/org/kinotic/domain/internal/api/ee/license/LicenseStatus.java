package org.kinotic.domain.internal.api.ee.license;

/**
 * Where a {@link License} stands at a given time.
 */
public enum LicenseStatus {
    /** Before its expiry: its features are on. */
    ACTIVE,
    /** Expired less than {@link License#GRACE_PERIOD} ago: its features stay on until the grace period ends. */
    GRACE_PERIOD,
    /** Expired more than {@link License#GRACE_PERIOD} ago: its features are off. */
    EXPIRED
}
