package org.kinotic.os.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.InviteEmailTemplateRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.os.api.services.InviteEmailTemplateService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultInviteEmailTemplateService extends AbstractApplicationScopedService<InviteEmailTemplate>
        implements InviteEmailTemplateService {

    private final InviteEmailTemplateRepository inviteEmailTemplateRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

    public DefaultInviteEmailTemplateService(InviteEmailTemplateRepository repository,
                                             SecurityContext securityContext,
                                             ApplicationRepository applicationRepository,
                                             EmailService emailService) {
        super(repository, securityContext);
        this.inviteEmailTemplateRepository = repository;
        this.applicationRepository = applicationRepository;
        this.emailService = emailService;
    }

    @Override
    public CompletableFuture<InviteEmailTemplate> findByApplication(String applicationId) {
        Validate.notBlank(applicationId, "applicationId is required");
        return inviteEmailTemplateRepository.findByApplication(applicationId, requireOrganizationId());
    }

    @Override
    public CompletableFuture<InviteEmailTemplate> save(InviteEmailTemplate entity) {
        Validate.notBlank(entity.getApplicationId(), "applicationId is required");
        Validate.notBlank(entity.getSubject(), "subject is required");
        Validate.notBlank(entity.getHtmlBody(), "htmlBody is required");
        Validate.notBlank(entity.getTextBody(), "textBody is required");

        String organizationId = requireOrganizationId();
        entity.setOrganizationId(organizationId);

        // Fails at save rather than at the next send.
        emailService.validateInviteTemplate(entity.getSubject(), entity.getHtmlBody(), entity.getTextBody());

        return applicationRepository.findById(entity.getApplicationId(), organizationId)
                .thenCompose(app -> {
                    if (app == null) {
                        return CompletableFuture.<InviteEmailTemplate>failedFuture(
                                new IllegalArgumentException("Application not found: " + entity.getApplicationId()));
                    }
                    return inviteEmailTemplateRepository.findByApplication(entity.getApplicationId(), organizationId);
                })
                .thenCompose(existing -> {
                    // At most one template per application: a new save adopts the existing row's
                    // id so edits update in place instead of creating a duplicate.
                    if (entity.getId() == null) {
                        entity.setId(existing != null ? existing.getId() : UUID.randomUUID().toString());
                    } else if (existing != null && !existing.getId().equals(entity.getId())) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "A template already exists for this application."));
                    }
                    Date now = new Date();
                    if (entity.getCreated() == null) {
                        entity.setCreated(existing != null ? existing.getCreated() : now);
                    }
                    entity.setUpdated(now);
                    return super.save(entity);
                });
    }
}
