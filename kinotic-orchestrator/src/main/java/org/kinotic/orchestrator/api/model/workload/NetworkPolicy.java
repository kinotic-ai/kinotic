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
     * The outbound destinations the VM may reach, including the api-gateway the workload is
     * deployed to talk to. Applies only when {@link #mode} is {@link NetworkMode#ENABLED}.
     *
     * What an entry may be, and what an empty list means, follow the {@link VmProviderType} of
     * the node the workload is placed on. {@link VmProviderType#CLOUD_HYPERVISOR} takes IPv4
     * addresses and CIDRs, enforced by the node's firewall, and reaches nothing beyond name
     * resolution when the list is empty. {@link VmProviderType#BOXLITE} takes hostnames,
     * enforced on the connection so an unlisted destination is unreachable by name and by raw
     * IP alike, and grants unrestricted egress when the list is empty.
     */
    private List<String> allowedHosts = new ArrayList<>();

}
