package org.kinotic.os.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.os.api.model.security.PendingInviteSummary;
import org.kinotic.os.api.services.SystemOrganizationService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultSystemOrganizationService implements SystemOrganizationService {

    private final ApplicationRepository applicationRepository;
    private final ProjectRepository projectRepository;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final OrganizationService organizationService;

    @Override
    public CompletableFuture<Page<Organization>> findOrganizations(Pageable pageable) {
        return organizationService.findAll(pageable).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<Organization>> searchOrganizations(String searchText, Pageable pageable) {
        return organizationService.search(searchText, pageable).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Organization> findOrganizationById(String organizationId) {
        return organizationService.findById(organizationId).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> countOrganizations() {
        return organizationService.count().toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<Application>> findApplications(String organizationId, Pageable pageable) {
        return applicationRepository.findAll(organizationId, pageable).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<Project>> findProjects(String organizationId, Pageable pageable) {
        return projectRepository.findAll(organizationId, pageable).toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> findMembers(String organizationId,
                                                                        String applicationId,
                                                                        Pageable pageable) {
        return identityService.findUsersByScope(organizationId, applicationId, pageable)
                              .toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<UserParticipantIdentity>> searchMembers(String searchText,
                                                                          String organizationId,
                                                                          String applicationId,
                                                                          Pageable pageable) {
        return identityService.searchUsersByScope(searchText, organizationId, applicationId, pageable)
                              .toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Page<PendingInviteSummary>> findPendingInvites(String organizationId,
                                                                            String applicationId,
                                                                            Pageable pageable) {
        return inviteService.findPendingInvites(organizationId, applicationId, pageable)
                            .thenApply(page -> page.map(PendingInviteSummary::from));
    }
}
