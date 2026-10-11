package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service whose functions all require the one permission it names, but for the one that names its own.
 */
@AuthzResource(value = "platform", resourceId = "kinotic", permission = "can_manage_workloads")
public interface TestWorkloadService {

    CompletableFuture<Void> deployWorkload(String workloadId);

    CompletableFuture<Void> deleteWorkload(String workloadId);

    @AuthzCheck(permission = "can_view_workloads")
    CompletableFuture<Void> findWorkload(String workloadId);

}
