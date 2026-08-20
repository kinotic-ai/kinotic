package org.kinotic.orchestrator.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Whether a {@link VmNode} is fit to receive workloads, and why when it is not.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VmNodeStatus {

    /**
     * Whether the node is taking workloads.
     */
    private VmNodeStatusType type = VmNodeStatusType.ONLINE;

    /**
     * Why the node is not taking workloads, or null when it is. Set from the node's own report
     * of the guarantees it can still make — a data root that stopped enforcing disk limits, or
     * a firewall that stopped hiding host credentials from guests.
     */
    private String healthMessage;

    public VmNodeStatus(VmNodeStatusType type, String healthMessage) {
        this.type = type;
        this.healthMessage = healthMessage;
    }
}
