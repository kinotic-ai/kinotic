package org.kinotic.core.internal.api.event;

import java.io.Serializable;

/**
 * The key of a {@link SampleClusterEvent}: the detail is whatever the test needs the event to carry.
 */
record SampleClusterEventKey(String organizationId, String applicationId, Serializable detail) implements Serializable {
}
