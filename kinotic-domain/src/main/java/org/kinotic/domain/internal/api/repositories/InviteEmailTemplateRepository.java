package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class InviteEmailTemplateRepository extends AbstractApplicationScopedRepository<InviteEmailTemplate> {

    public InviteEmailTemplateRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_invite_email_template", InviteEmailTemplate.class, crudServiceTemplate);
    }

    /** Finds the application's invitation template, or {@code null} if none exists. */
    public Future<InviteEmailTemplate> findByApplication(String applicationId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findFirst(b -> b.routing(orgId).query(composeOrgFilter(orgId,
                applicationIdFilter(applicationId))));
    }
}
