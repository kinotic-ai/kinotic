package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationRepository extends AbstractRepository<Organization> {

    public OrganizationRepository(ElasticsearchAsyncClient esAsyncClient,
                                  CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_organization", Organization.class, esAsyncClient, crudServiceTemplate);
    }
}
