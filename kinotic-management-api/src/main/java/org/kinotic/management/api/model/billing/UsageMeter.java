package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A measure of an organization's use of Kinotic Cloud that a {@link PlanComponentType#METERED} component charges for.
 * Each meter is a Stripe Billing Meter, found by its {@link #getEventName() event name}.
 */
@Getter
@RequiredArgsConstructor
public enum UsageMeter {
    /** vCPUs of the organization's workload VMs, times the hours each ran. */
    VCPU_HOURS("kinotic_vcpu_hours", "vCPU hours"),
    /** Gigabytes of memory of the organization's workload VMs, times the hours each ran. */
    MEMORY_GB_HOURS("kinotic_memory_gb_hours", "Memory GB hours");

    /** The {@code event_name} of the meter's Stripe Billing Meter, which usage is reported under. */
    private final String eventName;

    /** The name invoices show for the meter. */
    private final String displayName;
}
