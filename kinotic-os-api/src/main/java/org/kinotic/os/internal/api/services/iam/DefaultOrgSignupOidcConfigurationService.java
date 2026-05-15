package org.kinotic.os.internal.api.services.iam;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.internal.api.repositories.OrgSignupOidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.os.api.services.iam.OrgSignupOidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultOrgSignupOidcConfigurationService
        extends AbstractCrudService<OrgSignupOidcConfiguration>
        implements OrgSignupOidcConfigurationService {

    private final OrgSignupOidcConfigurationRepository signupRepository;

    public DefaultOrgSignupOidcConfigurationService(OrgSignupOidcConfigurationRepository repository) {
        super(repository);
        this.signupRepository = repository;
    }

    @Override
    public CompletableFuture<OrgSignupOidcConfiguration> save(OrgSignupOidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OrgSignupOidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "OrgSignupOidcConfiguration provider cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<List<OrgSignupOidcConfiguration>> findAllEnabled() {
        return signupRepository.findAllEnabled();
    }

    @Override
    public CompletableFuture<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider) {
        Validate.notNull(provider, "provider cannot be null");
        return signupRepository.findEnabledByProvider(provider);
    }
}
