package org.kinotic.billing.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.billing.api.model.OrganizationBillingProfile;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrganizationBillingProfileRepository extends AbstractOrganizationScopedRepository<OrganizationBillingProfile> {

    public OrganizationBillingProfileRepository(ElasticsearchAsyncClient esAsyncClient,
                                                CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_organization_billing_profile", OrganizationBillingProfile.class, esAsyncClient, crudServiceTemplate);
    }
}
