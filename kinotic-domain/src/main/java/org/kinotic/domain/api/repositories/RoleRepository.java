package org.kinotic.domain.api.repositories;

import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoleRepository extends AbstractOrganizationScopedRepository<Role> {

    public RoleRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_role", Role.class, crudServiceTemplate);
    }
}
