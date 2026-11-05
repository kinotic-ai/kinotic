package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAuthzModelService implements AuthzModelService {

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;

    @Override
    public Future<AuthzModel> findSystemModel() {
        return directory.findSystemDefinitions().map(generator::systemModel);
    }

    @Override
    public Future<AuthzModel> findApplicationModel(String organizationId, String applicationId) {
        return directory.findSystemDefinitions()
                        .compose(system -> directory.findApplicationDefinitions(organizationId, applicationId)
                                                    .map(services -> generator.applicationModel(system, services)));
    }
}
