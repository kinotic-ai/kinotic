package org.kinotic.billing.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.billing.api.model.RevenueSplit;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class RevenueSplitRepository extends AbstractOrganizationScopedRepository<RevenueSplit> {

    public RevenueSplitRepository(ElasticsearchAsyncClient esAsyncClient,
                                  CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_revenue_split", RevenueSplit.class, esAsyncClient, crudServiceTemplate);
    }
}
