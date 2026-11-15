package org.kinotic.management.api.model.billing;

/**
 * How usage of a {@link UsageMeter} is measured.
 */
public enum UsageMeasurementType {
    /**
     * A fixed amount for each hour something runs, such as the vCPUs of a microservice's VM, counted from the moment
     * it starts to the moment it stops.
     */
    RUNNING_RESOURCE,
    /** A level read once an hour, such as the gigabytes stored, which stands for the whole hour. */
    POLLED_LEVEL
}
