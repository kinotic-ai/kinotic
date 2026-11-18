package org.kinotic.management.internal.api.services.security;

import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.core.api.security.Participant;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.management.api.services.security.OwnedPasswordConfigurationService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultOwnedPasswordConfigurationService implements OwnedPasswordConfigurationService {

    private final PasswordConfigurationService passwordConfigurationService;
    private final SecurityContext securityContext;

    @Override
    public Future<Page<PasswordConfiguration>> findAll(Pageable pageable) {
        return passwordConfigurationService.findByOrganization(requireOwnerId(), pageable);
    }

    @Override
    public Future<PasswordConfiguration> save(PasswordConfiguration configuration) {
        Validate.notNull(configuration, "configuration is required");
        configuration.setOrganizationId(requireOwnerId());
        return passwordConfigurationService.save(configuration);
    }

    @Override
    public Future<Void> deleteById(String id) {
        return passwordConfigurationService.deleteById(id, requireOwnerId());
    }

    private String requireOwnerId() {
        return DomainUtil.signInOwnerIdOf(securityContext.requireParticipant(Participant.class));
    }
}
