package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.billing.UsageMeter;
import org.kinotic.management.api.model.deployment.MicroserviceDeployment;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.system.internal.api.model.BillableResource;
import org.kinotic.system.internal.api.model.MeteredAmount;
import org.kinotic.system.internal.api.repositories.BillableResourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Bills an organization for its microservices while they run: each run of a microservice's VM is a
 * {@link BillableResource} at the VM's vCPUs and memory per hour, from when the run started to when it ended. Calling it
 * again for the same deployment, from any number of servers at once, records each run once.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.billing.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MicroserviceBilling {

    private static final double MB_PER_GB = 1024;

    private final WorkloadRepository workloadRepository;
    private final BillableResourceRepository resourceRepository;

    /**
     * Brings the deployment's billed runs in line with its VMs: the run of a VM that is up is billed from when it
     * started, and the run of one that has stopped is billed until it stopped.
     */
    public Future<Void> track(MicroserviceDeployment deployment) {
        return forEachRun(deployment, workloadId -> track(deployment, workloadId));
    }

    /**
     * Ends billing for a deployment being removed: its runs are billed until their VMs stopped, and any VM not yet
     * known to have stopped until now.
     */
    public Future<Void> untrack(MicroserviceDeployment deployment) {
        return forEachRun(deployment, workloadId -> track(deployment, workloadId).compose(v -> end(workloadId, new Date())));
    }

    // the runs found open, and the deployment's current one, which may be too new for search to find
    private Future<Void> forEachRun(MicroserviceDeployment deployment, Function<String, Future<Void>> action) {
        return resourceRepository.findOpenForSubject(deployment.getId()).compose(open -> {
            Set<String> workloadIds = new LinkedHashSet<>();
            open.forEach(resource -> workloadIds.add(resource.getId()));
            if (deployment.getWorkloadId() != null) {
                workloadIds.add(deployment.getWorkloadId());
            }
            return Future.all(workloadIds.stream().map(action).toList()).mapEmpty();
        });
    }

    private Future<Void> track(MicroserviceDeployment deployment, String workloadId) {
        return workloadRepository.findById(workloadId).compose(workload -> {
            Future<Void> ret;
            if (workload == null) {
                ret = end(workloadId, new Date());
            } else if (workload.getStatus().isOpen()) {
                ret = start(deployment, workload);
            } else if (workload.getStatus().isComplete()) {
                ret = end(workloadId, workload.getUpdated() != null ? workload.getUpdated() : new Date());
            } else {
                // a run not started yet holds no VM
                ret = Future.succeededFuture();
            }
            return ret;
        });
    }

    private Future<Void> start(MicroserviceDeployment deployment, Workload workload) {
        return resourceRepository.findById(workload.getId()).compose(found -> found != null
                ? Future.succeededFuture()
                : resourceRepository.update(workload.getId(), stored -> Future.succeededFuture(stored != null ? stored : resourceOf(deployment, workload)))
                                    .mapEmpty());
    }

    private static BillableResource resourceOf(MicroserviceDeployment deployment, Workload workload) {
        Date now = new Date();
        return new BillableResource().setId(workload.getId())
                                     .setOrganizationId(deployment.getOrganizationId())
                                     .setSubjectId(deployment.getId())
                                     .setSubjectName(deployment.getName())
                                     .setAmounts(List.of(new MeteredAmount().setMeter(UsageMeter.VCPU_HOURS).setPerHour(workload.getCpus()),
                                                         new MeteredAmount().setMeter(UsageMeter.MEMORY_GB_HOURS)
                                                                            .setPerHour(workload.getMemoryMb() / MB_PER_GB)))
                                     .setStarted(workload.getCreated() != null ? workload.getCreated() : now)
                                     .setCreated(now)
                                     .setUpdated(now);
    }

    // a run never billed while it ran is left unbilled, rather than created already ended
    private Future<Void> end(String workloadId, Date ended) {
        return resourceRepository.findById(workloadId).compose(found -> found == null || found.getEnded() != null
                ? Future.succeededFuture()
                : resourceRepository.update(workloadId, stored -> {
                    if (stored.getEnded() == null) {
                        stored.setEnded(ended).setUpdated(new Date());
                    }
                    return Future.succeededFuture(stored);
                }).mapEmpty());
    }
}
