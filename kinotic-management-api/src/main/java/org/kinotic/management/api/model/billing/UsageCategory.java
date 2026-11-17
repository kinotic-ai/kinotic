package org.kinotic.management.api.model.billing;

/**
 * The group a {@link UsageMeter}'s usage is shown in.
 */
public enum UsageCategory {
    /** The VMs the organization's microservices run in. */
    COMPUTE,
    /** The data the organization keeps. */
    STORAGE
}
