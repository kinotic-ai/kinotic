package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
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

    private static final long AWAIT_POLL_MILLIS = 1_000;
    private static final long AWAIT_TIMEOUT_MILLIS = 300_000;

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreRepository stores;
    private final Vertx vertx;

    @Override
    public Future<AuthzModel> findSystemModel() {
        return directory.findSystemDefinitions().map(generator::systemModel);
    }

    @Override
    public Future<AuthzModel> awaitSystemModel(Collection<String> roleIds) {
        return awaitSystemModel(roleIds, System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS);
    }

    private Future<AuthzModel> awaitSystemModel(Collection<String> roleIds, long deadline) {
        return Future.all(stores.findById(AuthzStore.SYSTEM), findSystemModel())
                     // a store or a directory that cannot be read yet is read again, like a store that runs another model
                     .transform(read -> {
                         AuthzModel model = null;
                         if (read.succeeded()) {
                             AuthzStore store = read.result().resultAt(0);
                             AuthzModel implied = read.result().resultAt(1);
                             // a model written before a server's own services were registered lacks their roles, so
                             // the store must run the model the directory implies now, and that model must have them
                             AuthzModelRevision running = store == null ? null : store.getState().getObserved();
                             if (running != null && running.hash().equals(implied.hash()) && implied.roles().keySet().containsAll(roleIds)) {
                                 model = implied;
                             }
                         }
                         Future<AuthzModel> ret;
                         if (model != null) {
                             ret = Future.succeededFuture(model);
                         } else if (System.currentTimeMillis() >= deadline) {
                             ret = Future.failedFuture(new IllegalStateException("The system store does not run a model with the roles " + roleIds));
                         } else {
                             ret = vertx.timer(AWAIT_POLL_MILLIS).compose(t -> awaitSystemModel(roleIds, deadline));
                         }
                         return ret;
                     });
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
