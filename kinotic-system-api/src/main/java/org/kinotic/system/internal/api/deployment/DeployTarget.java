package org.kinotic.system.internal.api.deployment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where one deployment run puts the project: the node holding the checkout directory, the
 * absolute host directory of the checkout, and the runtime workload already serving it —
 * {@code null} when this run must create it (first deployment of the project).
 */
@Getter
@RequiredArgsConstructor
public class DeployTarget {

    private final String nodeId;

    private final String hostDir;

    private final String runtimeWorkloadId;

}
