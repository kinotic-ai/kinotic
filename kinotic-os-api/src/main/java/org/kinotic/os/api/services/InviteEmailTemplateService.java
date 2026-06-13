package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;

import java.util.concurrent.CompletableFuture;

/**
 * CRUD service for an application's customized invitation email — at most one
 * {@link InviteEmailTemplate} per application. Saving validates the Handlebars sources, so
 * a broken template is rejected instead of breaking the next send. Deleting the template
 * reverts the application to the built-in invitation email.
 */
@Publish
public interface InviteEmailTemplateService extends ApplicationScopedCrudService<InviteEmailTemplate, String> {

    /**
     * Finds the application's invitation template, or {@code null} when the application
     * uses the built-in email.
     */
    CompletableFuture<InviteEmailTemplate> findByApplication(String applicationId);

}
