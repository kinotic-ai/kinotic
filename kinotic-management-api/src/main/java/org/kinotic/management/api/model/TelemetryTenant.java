package org.kinotic.management.api.model;

import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.stream.Collectors;

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
        return "{workload_id=\"" + quoted(workloadId) + "\"}";
    }

    /**
     * @param workloadIds the ids of workloads in one tenant
     * @return the LogQL selector of every log stream any of them wrote, one selector for all
     */
    public static String workloadLogSelector(Collection<String> workloadIds) {
        Validate.notEmpty(workloadIds, "workloadIds cannot be empty");
        String ret;
        if (workloadIds.size() == 1) {
            ret = workloadLogSelector(workloadIds.iterator().next());
        } else {
            // each id is a literal inside the regex matcher, so a metacharacter in one matches itself alone
            ret = "{workload_id=~\"" + workloadIds.stream().map(id -> quoted(literal(id))).collect(Collectors.joining("|")) + "\"}";
        }
        return ret;
    }

    private static String quoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // RE2 has no quoting form of its own, so every metacharacter is escaped one by one
    private static String literal(String value) {
        StringBuilder ret = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                ret.append('\\');
            }
            ret.append(c);
        }
        return ret.toString();
    }
}
