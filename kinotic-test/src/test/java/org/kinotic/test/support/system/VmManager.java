package org.kinotic.test.support.system;

import io.vertx.core.Future;
import org.kinotic.management.api.model.workload.Workload;

import java.util.List;

/**
 * What a node's vm-manager serves, as {@link org.kinotic.system.api.services.workload.VmManagerProxy} invokes
 * it on the node's scoped address with the routing parameter stripped. A test registers an
 * implementation under a node's address, and the platform's own proxy reaches it over the RPC.
 */
public interface VmManager {

    Future<Workload> startWorkload(Workload workload);

    Future<Void> stopWorkload(String workloadId);

    Future<Void> destroyWorkload(String workloadId);

    Future<Workload> getWorkload(String workloadId);

    Future<List<Workload>> listWorkloads();

}
