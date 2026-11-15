package org.kinotic.management.api.model.billing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A type of billable usage of Kinotic Cloud, which a {@link PlanComponentType#METERED} component of a plan sets the rate
 * of. Each meter is a Stripe Billing Meter, found by its {@link #getEventName() event name}, and usage of it is recorded
 * each hour as {@link UsageLine}s of a {@link UsageRecord}.
 */
@Getter
@RequiredArgsConstructor
public enum UsageMeter {
    /** vCPUs of the organization's running microservices, times the hours each ran. */
    VCPU_HOURS("kinotic_vcpu_hours", "vCPU", "vCPU-hour", UsageCategory.COMPUTE, UsageMeasurementType.RUNNING_RESOURCE),
    /** Gigabytes of memory of the organization's running microservices, times the hours each ran. */
    MEMORY_GB_HOURS("kinotic_memory_gb_hours", "Memory", "GB-hour", UsageCategory.COMPUTE, UsageMeasurementType.RUNNING_RESOURCE),
    /**
     * Gigabytes the organization's entity data takes up, over time: a gigabyte stored for a whole month is one
     * GB-month.
     */
    DATA_STORAGE_GB_MONTHS("kinotic_data_storage_gb_months", "Data storage", "GB-month", UsageCategory.STORAGE,
                           UsageMeasurementType.POLLED_LEVEL);

    /** The {@code event_name} of the meter's Stripe Billing Meter, which usage is reported under. */
    private final String eventName;

    /** The name invoices and the portal show for the meter. */
    private final String displayName;

    /** The unit a quantity of the meter is in, such as {@code GB-month}. */
    private final String unit;

    /** The group the portal shows the meter's usage in. */
    private final UsageCategory category;

    /** How usage of the meter is measured. */
    private final UsageMeasurementType measurementType;
}
