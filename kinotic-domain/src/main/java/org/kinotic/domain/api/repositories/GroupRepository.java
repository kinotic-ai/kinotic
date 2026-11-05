package org.kinotic.domain.api.repositories;

import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class GroupRepository extends AbstractOrganizationScopedRepository<Group> {

    public GroupRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_group", Group.class, crudServiceTemplate);
    }
}
