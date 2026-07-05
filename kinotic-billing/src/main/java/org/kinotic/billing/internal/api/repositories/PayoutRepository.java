package org.kinotic.billing.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.billing.api.model.Payout;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class PayoutRepository extends AbstractOrganizationScopedRepository<Payout> {

    public PayoutRepository(ElasticsearchAsyncClient esAsyncClient,
                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_payout", Payout.class, esAsyncClient, crudServiceTemplate);
    }
}
