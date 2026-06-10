package org.kinotic.os.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.EmailTemplateKey;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.EmailTemplateRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.internal.api.services.EmailService;
import org.kinotic.os.api.services.EmailTemplateService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultEmailTemplateService extends AbstractApplicationScopedService<EmailTemplate>
        implements EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

    public DefaultEmailTemplateService(EmailTemplateRepository repository,
                                       SecurityContext securityContext,
                                       ApplicationRepository applicationRepository,
                                       EmailService emailService) {
        super(repository, securityContext);
        this.emailTemplateRepository = repository;
        this.applicationRepository = applicationRepository;
        this.emailService = emailService;
    }

    @Override
    public CompletableFuture<EmailTemplate> findByApplicationAndKey(String applicationId, EmailTemplateKey templateKey) {
        Validate.notBlank(applicationId, "applicationId is required");
        Validate.notNull(templateKey, "templateKey is required");
        return emailTemplateRepository.findByKey(applicationId, templateKey, requireOrganizationId());
    }

    @Override
    public CompletableFuture<EmailTemplate> save(EmailTemplate entity) {
        Validate.notBlank(entity.getApplicationId(), "applicationId is required");
        Validate.notNull(entity.getTemplateKey(), "templateKey is required");
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
                        return CompletableFuture.<EmailTemplate>failedFuture(
                                new IllegalArgumentException("Application not found: " + entity.getApplicationId()));
                    }
                    return emailTemplateRepository.findByKey(entity.getApplicationId(),
                                                             entity.getTemplateKey(),
                                                             organizationId);
                })
                .thenCompose(existing -> {
                    // One template per (application, key): a new save adopts the existing row's
                    // id so edits update in place instead of creating a duplicate.
                    if (entity.getId() == null) {
                        entity.setId(existing != null ? existing.getId() : UUID.randomUUID().toString());
                    } else if (existing != null && !existing.getId().equals(entity.getId())) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "A template already exists for this application and key."));
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
