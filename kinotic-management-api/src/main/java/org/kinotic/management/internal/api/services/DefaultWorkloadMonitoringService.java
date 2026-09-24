package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
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
        // a platform workload carries no organization, so it is as absent as another organization's
        return workloadRepository.findById(workloadId)
                                 .map(workload -> DomainUtil.requireOwned(workload, organizationId, "Workload not found."));
    }

    private String requireOrganizationId() {
        // ApplicationParticipant is a sibling type, so app end-users are rejected here
        return securityContext.requireParticipant(OrganizationParticipant.class).getOrganizationId();
    }

}
