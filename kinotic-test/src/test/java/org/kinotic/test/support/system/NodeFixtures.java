package org.kinotic.test.support.system;

import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.system.api.model.workload.VmNodeRegistration;
import org.kinotic.system.api.services.workload.VmManagerProxy;

/**
 * A node as a vm-manager registers it, and the address the platform's proxy sends to that node's
 * vm-manager at, for a suite that serves a {@link StubVmManager} there.
 */
public final class NodeFixtures {

    private NodeFixtures() {
    }

    /** The registration a vm-manager sends at startup for a node of the given size. */
    public static VmNodeRegistration registration(String nodeId, int cpus, int memoryMb, int diskMb) {
        return new VmNodeRegistration().setId(nodeId)
                                       .setName(nodeId)
                                       .setHostname("host-" + nodeId)
                                       .setTotalCpus(cpus)
                                       .setTotalMemoryMb(memoryMb)
                                       .setTotalDiskMb(diskMb)
                                       .setWorkloadDataDir("/var/lib/kinotic/" + nodeId);
    }

    /** The address of the node's vm-manager: the proxy's identifier with the node id as its instance. */
    public static ServiceIdentifier vmManagerAddress(String nodeId) {
        ServiceIdentifier vmManager = KinoticUtil.serviceIdentifierOf(VmManagerProxy.class);
        return new ServiceIdentifier(vmManager.zone(), vmManager.namespace(), vmManager.name(), nodeId, vmManager.version());
    }
}
