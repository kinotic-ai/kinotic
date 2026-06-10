package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.EmailTemplateKey;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;

import java.util.concurrent.CompletableFuture;

/**
 * CRUD service for an application's {@link EmailTemplate} customizations. One template may
 * exist per application and {@link EmailTemplateKey}; saving validates the Handlebars
 * sources, so a broken template is rejected instead of breaking the next send. Deleting a
 * template reverts the application to the built-in email.
 */
@Publish
public interface EmailTemplateService extends ApplicationScopedCrudService<EmailTemplate, String> {

    /**
     * Finds the application's template for the given key, or {@code null} when the
     * application uses the built-in email.
     */
    CompletableFuture<EmailTemplate> findByApplicationAndKey(String applicationId, EmailTemplateKey templateKey);

}
