package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.management.api.model.Project;
import org.kinotic.domain.api.model.security.Member;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.model.PendingInviteSummary;
import org.kinotic.system.api.services.SystemOrganizationService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultSystemOrganizationService implements SystemOrganizationService {

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final ParticipantIdentityService identityService;
    private final MembershipService membershipService;
    private final InviteService inviteService;
    private final OrganizationService organizationService;

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
    public Future<Page<Application>> findApplications(String organizationId, Pageable pageable) {
        return applicationRepository.findAll(organizationId, pageable);
    }

    @Override
    public Future<Page<Project>> findProjects(String organizationId, Pageable pageable) {
        return projectRepository.findAll(organizationId, pageable);
    }

    @Override
    public Future<Page<Member>> findMembers(String organizationId,
                                            String applicationId,
                                            Pageable pageable) {
        // an application's members are its users
        return applicationId == null
                ? membershipService.findMembers(organizationId, pageable)
                : identityService.findUsersByScope(organizationId, applicationId, pageable).map(page -> page.map(Member::of));
    }

    @Override
    public Future<Page<Member>> searchMembers(String searchText,
                                              String organizationId,
                                              String applicationId,
                                              Pageable pageable) {
        return applicationId == null
                ? membershipService.searchMembers(searchText, organizationId, pageable)
                : identityService.searchUsersByScope(searchText, organizationId, applicationId, pageable).map(page -> page.map(Member::of));
    }

    @Override
    public Future<Page<PendingInviteSummary>> findPendingInvites(String organizationId,
                                                                 String applicationId,
                                                                 Pageable pageable) {
        return inviteService.findPendingInvites(organizationId, applicationId, pageable)
                            .map(page -> page.map(PendingInviteSummary::from));
    }
}
