package org.kinotic.core.internal.platform;

/**
 * Optional pre-load hook for {@link PlatformSecretsService}. When present in the Spring
 * context, its {@link #ensureFilesExist()} method runs before the service attempts to read
 * the platform secret files. Used by the development profile to seed local JSON key files
 * on first boot so the normal file-watch code path works identically to production.
 * <p>
 * Implementations must be idempotent — if the target files already exist, they should be
 * left untouched.
 */
public interface PlatformSecretsBootstrap {
    void ensureFilesExist();
}
