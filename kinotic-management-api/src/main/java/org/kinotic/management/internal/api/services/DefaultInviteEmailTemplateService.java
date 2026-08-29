package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.InviteEmailTemplateRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.management.api.services.InviteEmailTemplateService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

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
    public Future<InviteEmailTemplate> findByApplication(String applicationId) {
        Validate.notBlank(applicationId, "applicationId is required");
        return inviteEmailTemplateRepository.findByApplication(applicationId, requireOrganizationId());
    }

    @Override
    public Future<InviteEmailTemplate> save(InviteEmailTemplate entity) {
        Validate.notBlank(entity.getApplicationId(), "applicationId is required");
        Validate.notBlank(entity.getSubject(), "subject is required");
        Validate.notBlank(entity.getHtmlBody(), "htmlBody is required");
        Validate.notBlank(entity.getTextBody(), "textBody is required");

        String organizationId = requireOrganizationId();
        entity.setOrganizationId(organizationId);

        // Fails at save rather than at the next send.
        emailService.validateInviteTemplate(entity.getSubject(), entity.getHtmlBody(), entity.getTextBody());

        return applicationRepository.requireById(entity.getApplicationId(), organizationId)
                .compose(_ -> inviteEmailTemplateRepository.findByApplication(entity.getApplicationId(), organizationId))
                .compose(existing -> {
                    // At most one template per application: a new save adopts the existing row's
                    // id so edits update in place instead of creating a duplicate.
                    if (entity.getId() == null) {
                        entity.setId(existing != null ? existing.getId() : UUID.randomUUID().toString());
                    } else if (existing != null && !existing.getId().equals(entity.getId())) {
                        return Future.failedFuture(new IllegalArgumentException(
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
