package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.services.SecretStorageService;
import org.kinotic.system.api.model.llm.LlmConfiguration;
import org.kinotic.system.api.services.LlmConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.system.internal.api.repositories.LlmConfigurationRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultLlmConfigurationService implements LlmConfigurationService {

    // no organization can take the system's id, so the API keys never share a secret scope with an organization's
    private static final String SECRET_SCOPE = DomainUtil.SYSTEM_ID;

    private final LlmConfigurationRepository repository;
    private final SecretStorageService secretStorageService;
    private final SecurityContext securityContext;

    @Override
    public Future<Page<LlmConfiguration>> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Override
    public Future<LlmConfiguration> findById(String id) {
        Validate.notBlank(id, "id is required");
        return repository.findById(id);
    }

    @Override
    public Future<LlmConfiguration> findByName(String name) {
        Validate.notBlank(name, "name is required");
        return repository.findByName(name);
    }

    @Override
    public Future<LlmConfiguration> save(LlmConfiguration configuration, String apiKey) {
        Validate.notNull(configuration, "configuration is required");
        Validate.notBlank(configuration.getName(), "name is required");
        Validate.notNull(configuration.getProvider(), "provider is required");
        Validate.notBlank(configuration.getModel(), "model is required");
        Validate.isTrue(configuration.getTemperature() == null
                        || (configuration.getTemperature() >= 0
                            && configuration.getTemperature() <= configuration.getProvider().getMaxTemperature()),
                        "temperature must be from 0 to %s for %s", configuration.getProvider().getMaxTemperature(), configuration.getProvider());
        Validate.isTrue(configuration.getId() == null || StringUtils.isNotBlank(configuration.getId()), "id must not be blank");
        Date now = new Date();
        Future<LlmConfiguration> stored;
        if (configuration.getId() == null) {
            configuration.setId(UUID.randomUUID().toString())
                         .setCreated(now);
            stored = Future.succeededFuture();
        } else {
            // an update keeps the stored creation time, and a caller cannot pick the id a new configuration's key is held under
            stored = repository.findById(configuration.getId()).map(existing -> {
                Validate.isTrue(existing != null, "No LLM configuration has id '%s'", configuration.getId());
                configuration.setCreated(existing.getCreated());
                return existing;
            });
        }
        configuration.setUpdated(now);
        // the row is written once the vault holds the key, so it never claims a key the vault refused
        return stored.compose(existing -> repository.findByName(configuration.getName()).compose(named -> {
                         Validate.isTrue(named == null || named.getId().equals(configuration.getId()),
                                         "A configuration named '%s' already exists", configuration.getName());
                         Future<Boolean> held;
                         if (StringUtils.isNotBlank(apiKey)) {
                             held = secretStorageService.setSecret(SECRET_SCOPE, configuration.getId(), apiKey).map(true);
                         } else {
                             held = Future.succeededFuture(existing != null && existing.isApiKeyHeld());
                         }
                         return held;
                     }))
                         // refreshed before returning, since the console's table reads the configurations right after a save
                         .compose(held -> repository.saveSync(configuration.setApiKeyHeld(held)));
    }

    @Override
    public Future<String> findApiKey(String id) {
        // system users manage the key but never read it back; every gateway binds the connection's participant to the
        // calls it forwards, so a call without one comes from a cluster member's own code
        if (securityContext.currentParticipant() != null) {
            throw new AuthorizationException("Access denied");
        }
        return findById(id).compose(configuration -> configuration != null && configuration.isApiKeyHeld()
                ? secretStorageService.getSecret(SECRET_SCOPE, id)
                : Future.succeededFuture());
    }

    @Override
    public Future<Void> deleteById(String id) {
        return findById(id).compose(configuration -> {
            Future<Void> ret;
            if (configuration == null) {
                ret = Future.succeededFuture();
            } else {
                Future<Void> keyDeleted = configuration.isApiKeyHeld()
                        ? secretStorageService.deleteSecret(SECRET_SCOPE, id)
                        : Future.succeededFuture();
                ret = keyDeleted.compose(v -> repository.deleteByIdSync(id));
            }
            return ret;
        });
    }
}
