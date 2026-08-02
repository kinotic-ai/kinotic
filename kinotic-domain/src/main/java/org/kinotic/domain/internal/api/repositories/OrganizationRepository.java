package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrganizationRepository extends AbstractRepository<Organization> {

    public OrganizationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_organization", Organization.class, crudServiceTemplate);
    }
}
