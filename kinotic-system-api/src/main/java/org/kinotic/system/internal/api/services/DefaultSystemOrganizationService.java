package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.model.Project;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.model.security.PendingInviteSummary;
import org.kinotic.system.api.services.DeploymentOperationsService;
import org.kinotic.system.api.services.SystemOrganizationService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultSystemOrganizationService implements SystemOrganizationService {

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final DeploymentOperationsService deploymentOperationsService;

    @Override
    public Future<Page<Organization>> findOrganizations(Pageable pageable) {
        return organizationService.findAll(pageable);
    }

    @Override
    public Future<Page<Organization>> searchOrganizations(String searchText, Pageable pageable) {
        return organizationService.search(searchText, pageable);
    }

    @Override
    public Future<Organization> findOrganizationById(String organizationId) {
        return organizationService.findById(organizationId);
    }

    @Override
    public Future<Long> countOrganizations() {
        return organizationService.count();
    }

    @Override
    public Future<Organization> provisionOrganization(String organizationId) {
        return deploymentOperationsService.provisionOrganization(organizationId);
    }

    @Override
    public Future<Page<Application>> findApplications(String organizationId, Pageable pageable) {
        return applicationRepository.findAll(organizationId, pageable);
    }

    @Override
    public Future<Page<Project>> findProjects(String organizationId, Pageable pageable) {
        return projectRepository.findAll(organizationId, pageable);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> findMembers(String organizationId,
                                                             String applicationId,
                                                             Pageable pageable) {
        return identityService.findUsersByScope(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<UserParticipantIdentity>> searchMembers(String searchText,
                                                               String organizationId,
                                                               String applicationId,
                                                               Pageable pageable) {
        return identityService.searchUsersByScope(searchText, organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<PendingInviteSummary>> findPendingInvites(String organizationId,
                                                                 String applicationId,
                                                                 Pageable pageable) {
        return inviteService.findPendingInvites(organizationId, applicationId, pageable)
                            .map(page -> page.map(PendingInviteSummary::from));
    }
}
