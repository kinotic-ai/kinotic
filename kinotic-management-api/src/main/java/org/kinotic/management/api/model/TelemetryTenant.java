package org.kinotic.management.api.model;

import org.apache.commons.lang3.Validate;

/**
 * How a workload's telemetry is addressed in the Grafana backends: the tenant it is shipped under, and
 * the selector of its log streams within that tenant. Both must match what the vm-manager's
 * AlloyManager ships.
 */
public final class TelemetryTenant {

    /**
     * Tenant that receives the logs, traces, and metrics of platform workloads with no organization
     * (SYSTEM scope). Organization ids can never take this value — ids beginning with "kinotic" are
     * reserved for the platform.
     */
    public static final String SYSTEM = "kinotic-system";

    private TelemetryTenant() {
    }

    /**
     * @param organizationId the organization a workload runs on behalf of, or null for a platform workload
     * @return the tenant holding that workload's telemetry
     */
    public static String of(String organizationId) {
        return organizationId != null ? organizationId : SYSTEM;
    }

    /**
     * @param workloadId a workload's id
     * @return the LogQL selector of every log stream the workload wrote
     */
    public static String workloadLogSelector(String workloadId) {
        Validate.notBlank(workloadId, "workloadId cannot be blank");
        // The id is quoted into a label matcher, so a quote or backslash in it cannot widen the selector
        return "{workload_id=\"" + workloadId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }
}
