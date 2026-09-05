package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.internal.api.repositories.OrgSignupOidcConfigurationRepository;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

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
    protected Future<Void> beforeSave(OrgSignupOidcConfiguration entity) {
        Validate.notNull(entity.getName(), "OrgSignupOidcConfiguration name cannot be null");
        Validate.notNull(entity.getProvider(), "OrgSignupOidcConfiguration provider cannot be null");
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreated(new Date());
        }
        entity.setUpdated(new Date());
        return Future.succeededFuture();
    }

    @Override
    public Future<List<OrgSignupOidcConfiguration>> findAllEnabled() {
        return signupRepository.findAllEnabled();
    }

    @Override
    public Future<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider) {
        Validate.notNull(provider, "provider cannot be null");
        return signupRepository.findEnabledByProvider(provider);
    }
}
