package org.kinotic.os.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.SystemOidcConfiguration;
import org.kinotic.domain.internal.api.repositories.SystemOidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.os.api.services.iam.SystemOidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultSystemOidcConfigurationService
        extends AbstractCrudService<SystemOidcConfiguration>
        implements SystemOidcConfigurationService {

    private final SystemOidcConfigurationRepository systemOidcRepository;

    public DefaultSystemOidcConfigurationService(SystemOidcConfigurationRepository repository) {
        super(repository);
        this.systemOidcRepository = repository;
    }

    @Override
    public CompletableFuture<SystemOidcConfiguration> save(SystemOidcConfiguration entity) {
        Validate.notNull(entity.getName(), "SystemOidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "SystemOidcConfiguration provider cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<SystemOidcConfiguration>> findAllEnabled() {
        return systemOidcRepository.findAllEnabled();
    }
}
