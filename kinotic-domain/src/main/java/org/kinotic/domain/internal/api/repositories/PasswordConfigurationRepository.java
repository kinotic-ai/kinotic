package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class PasswordConfigurationRepository extends AbstractOrganizationScopedRepository<PasswordConfiguration> {

    public PasswordConfigurationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_password_configuration", PasswordConfiguration.class, crudServiceTemplate);
    }
}
