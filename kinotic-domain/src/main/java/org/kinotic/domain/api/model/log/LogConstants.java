package org.kinotic.domain.api.model.log;

/**
 * Shared constants of the log pipeline.
 */
public final class LogConstants {

    /**
     * Loki tenant that receives logs for platform workloads with no organization (SYSTEM scope).
     * Organization ids can never take this value — ids beginning with "kinotic" are reserved
     * for the platform.
     */
    public static final String SYSTEM_LOG_TENANT = "kinotic-system";

    private LogConstants() {
    }
}
