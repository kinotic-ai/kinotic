/**
 * Loki tenant that receives logs for platform workloads with no organization (SYSTEM scope).
 * Organization ids can never take this value — ids beginning with "kinotic" are reserved
 * for the platform.
 */
export const SYSTEM_LOG_TENANT = 'kinotic-system'
