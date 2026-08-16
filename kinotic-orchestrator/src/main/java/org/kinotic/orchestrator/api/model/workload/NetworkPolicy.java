package org.kinotic.orchestrator.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * The network access granted to a {@link Workload}'s VM. The workload's own image and
 * entrypoint have no say in it: the policy is applied by the VM provider when the VM is
 * created, which is what makes it safe to run code the platform does not trust.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class NetworkPolicy {

    /**
     * Whether the VM has network access at all.
     */
    private NetworkMode mode = NetworkMode.ENABLED;

    /**
     * The outbound destinations the VM may reach, as hostnames. A populated list is enforced
     * on the connection, so an unlisted destination is unreachable by name and by raw IP
     * alike. An empty list is not a denial: it grants unrestricted egress. Applies only when
     * {@link #mode} is {@link NetworkMode#ENABLED}.
     */
    private List<String> allowedHosts = new ArrayList<>();

}
