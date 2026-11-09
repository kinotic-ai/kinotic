package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

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
        return applicationModel(organizationId, applicationId, UnaryOperator.identity());
    }

    @Override
    public Future<AuthzModel> findApplicationModel(String organizationId, String applicationId, ServiceDefinition candidate) {
        return applicationModel(organizationId, applicationId, services -> with(services, candidate));
    }

    private Future<AuthzModel> applicationModel(String organizationId,
                                                String applicationId,
                                                UnaryOperator<List<ServiceDefinition>> services) {
        return directory.findSystemDefinitions()
                        .compose(system -> directory.findApplicationDefinitions(organizationId, applicationId)
                                                    .map(registered -> generator.applicationModel(system, services.apply(registered))));
    }

    // the directory stores a service under its qualified name, so the candidate takes the place of the one it holds
    private static List<ServiceDefinition> with(List<ServiceDefinition> services, ServiceDefinition candidate) {
        List<ServiceDefinition> ret = new ArrayList<>(services.size() + 1);
        for (ServiceDefinition service : services) {
            if (!service.getQualifiedName().equals(candidate.getQualifiedName())) {
                ret.add(service);
            }
        }
        ret.add(candidate);
        return ret;
    }
}
