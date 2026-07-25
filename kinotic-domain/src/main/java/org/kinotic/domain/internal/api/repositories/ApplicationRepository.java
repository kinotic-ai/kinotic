package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApplicationRepository extends AbstractOrganizationScopedRepository<Application> {

    public ApplicationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_application", Application.class, crudServiceTemplate);
    }
}
