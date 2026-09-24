package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.workload.Workload;
import org.kinotic.management.api.repositories.WorkloadRepository;
import org.kinotic.management.api.services.WorkloadMonitoringService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultWorkloadMonitoringService implements WorkloadMonitoringService {

    private final SecurityContext securityContext;
    private final WorkloadRepository workloadRepository;

    @Override
    public Future<Page<Workload>> findWorkloads(Pageable pageable) {
        return workloadRepository.findAllForOrganization(requireOrganizationId(), pageable);
    }

    @Override
    public Future<Page<Workload>> findWorkloadsForApplication(String applicationId, Pageable pageable) {
        Validate.notBlank(applicationId, "applicationId is required");
        return workloadRepository.findAllForApplication(requireOrganizationId(), applicationId, pageable);
    }

    @Override
    public Future<Workload> findWorkload(String workloadId) {
        Validate.notBlank(workloadId, "workloadId is required");
        String organizationId = requireOrganizationId();
        return workloadRepository.findById(workloadId)
                                 .map(workload -> {
                                     // A workload runs for an organization rather than belonging to one, and a
                                     // platform workload names none; every miss fails alike, so the message is no
                                     // existence oracle
                                     if (workload == null || !organizationId.equals(workload.getOrganizationId())) {
                                         throw new IllegalArgumentException("Workload not found.");
                                     }
                                     return workload;
                                 });
    }

    private String requireOrganizationId() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }

}
