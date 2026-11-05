package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;

/**
 * CRUD service for an application's customized invitation email — at most one
 * {@link InviteEmailTemplate} per application. Saving validates the Handlebars sources, so
 * a broken template is rejected instead of breaking the next send. Deleting the template
 * reverts the application to the built-in invitation email.
 */
@Publish
@AuthzResource(value = AuthzConstants.APPLICATION_TYPE, parent = AuthzConstants.ORGANIZATION_TYPE)
public interface InviteEmailTemplateService extends ApplicationScopedCrudService<InviteEmailTemplate, String> {

    @AuthzCheck(permission = AuthzConstants.CAN_EDIT, resourceId = "{entity.applicationId}")
    @Override
    Future<InviteEmailTemplate> save(InviteEmailTemplate entity);

    @AuthzCheck(permission = AuthzConstants.CAN_EDIT, resourceId = "{entity.applicationId}")
    @Override
    Future<InviteEmailTemplate> saveSync(InviteEmailTemplate entity);

    @AuthzCheck(resource = AuthzConstants.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
    @Override
    Future<InviteEmailTemplate> findById(String id);

    @AuthzCheck(permission = AuthzConstants.CAN_EDIT, resource = AuthzConstants.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
    @Override
    Future<Void> deleteById(String id);

    @AuthzCheck(permission = AuthzConstants.CAN_EDIT, resource = AuthzConstants.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
    @Override
    Future<Void> deleteByIdSync(String id);

    /**
     * Finds the application's invitation template, or {@code null} when the application
     * uses the built-in email.
     */
    Future<InviteEmailTemplate> findByApplication(String applicationId);

}
