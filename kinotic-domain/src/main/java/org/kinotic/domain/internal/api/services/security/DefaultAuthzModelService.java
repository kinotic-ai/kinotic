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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    public Future<AuthzModel> findApplicationModel(String organizationId, String applicationId, Collection<ServiceDefinition> candidates) {
        return applicationModel(organizationId, applicationId, services -> with(services, candidates));
    }

    private Future<AuthzModel> applicationModel(String organizationId,
                                                String applicationId,
                                                UnaryOperator<List<ServiceDefinition>> services) {
        return directory.findSystemDefinitions()
                        .compose(system -> directory.findApplicationDefinitions(organizationId, applicationId)
                                                    .map(registered -> generator.applicationModel(system, services.apply(registered))));
    }

    // the directory stores a service under its qualified name, so each candidate takes the place of the one it holds
    private static List<ServiceDefinition> with(List<ServiceDefinition> services, Collection<ServiceDefinition> candidates) {
        Set<String> replaced = new HashSet<>();
        for (ServiceDefinition candidate : candidates) {
            replaced.add(candidate.getQualifiedName());
        }
        List<ServiceDefinition> ret = new ArrayList<>(services.size() + candidates.size());
        for (ServiceDefinition service : services) {
            if (!replaced.contains(service.getQualifiedName())) {
                ret.add(service);
            }
        }
        ret.addAll(candidates);
        return ret;
    }
}
