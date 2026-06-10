package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.EmailTemplateKey;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EmailTemplateRepository extends AbstractApplicationScopedRepository<EmailTemplate> {

    public EmailTemplateRepository(ElasticsearchAsyncClient esAsyncClient,
                                   CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_email_template", EmailTemplate.class, esAsyncClient, crudServiceTemplate);
    }

    /** Finds the application's template for the given key, or {@code null} if none exists. */
    public CompletableFuture<EmailTemplate> findByKey(String applicationId, EmailTemplateKey key, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findFirst(b -> b.routing(orgId).query(composeOrgFilter(orgId,
                applicationIdFilter(applicationId),
                termFilter("templateKey", key.name()))));
    }
}
