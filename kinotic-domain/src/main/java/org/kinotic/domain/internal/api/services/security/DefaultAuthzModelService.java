package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.ServiceModelNames;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.internal.api.model.ServiceDirectoryRecord;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
public class DefaultAuthzModelService implements AuthzModelService {

    private static final long AWAIT_POLL_MILLIS = 1_000;
    private static final long AWAIT_TIMEOUT_MILLIS = 300_000;

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreRepository stores;
    private final ServiceDirectoryEntryRepository entries;
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
        return Future.all(stores.findById(AuthzStoreService.SYSTEM), findSystemModel())
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
        return Future.all(systemDefinitions(), directory.findApplicationDefinitions(organizationId, applicationId))
                     .map(both -> generator.applicationModel(both.resultAt(0), both.resultAt(1)));
    }

    @Override
    public Future<AuthzModel> findApplicationModelOf(String organizationId,
                                                     String applicationId,
                                                     Collection<String> types,
                                                     Collection<String> permissions) {
        return partialModel(organizationId, applicationId, types, permissions, List.of());
    }

    @Override
    public Future<AuthzModel> findApplicationStoreModel(String organizationId, String applicationId) {
        return partialModel(organizationId, applicationId, List.of(), List.of(), List.of());
    }

    @Override
    public Future<AuthzModel> findApplicationModelOfRole(String organizationId, String applicationId, String roleId) {
        // derived inside the future, so an id that names no home type fails it
        return Future.succeededFuture(roleId)
                     .map(AuthzNameUtil::roleHome)
                     .compose(home -> partialModel(organizationId, applicationId, List.of(home), List.of(), List.of()));
    }

    @Override
    public Future<AuthzModel> findApplicationModelWith(String organizationId,
                                                       String applicationId,
                                                       Collection<ServiceDefinition> candidates,
                                                       Collection<String> types) {
        return partialModel(organizationId, applicationId, types, List.of(), candidates);
    }

    private Future<AuthzModel> partialModel(String organizationId,
                                            String applicationId,
                                            Collection<String> types,
                                            Collection<String> permissions,
                                            Collection<ServiceDefinition> candidates) {
        // the types the store places are in every model of it, so the services deciding them always take part
        Set<String> named = new TreeSet<>(AuthzModelGenerator.APPLICATION_TYPES);
        named.addAll(types);
        Set<String> required = new TreeSet<>(permissions);
        for (ServiceDefinition candidate : candidates) {
            ServiceModelNames names = generator.namesOf(candidate);
            named.addAll(names.named());
            // a permission of another type may carry the model name of a candidate's, which the generator refuses
            // only when it has both
            required.addAll(names.permissions());
        }
        return Future.all(systemDefinitions(), decidingTogether(organizationId, applicationId, named, required, new HashSet<>(), new TreeMap<>()))
                     .map(both -> generator.applicationModel(both.resultAt(0), with(both.resultAt(1), candidates)));
    }

    // The application's services deciding one of the types, placing a type inside one of them or requiring one of
    // the permissions, then those naming a type one of them names, until a read finds no type that was not read:
    // the services whose declarations a type's permissions and roles are made of. A type the store places is read
    // for the services deciding it alone, since every type of the application's services is inside one
    private Future<List<ServiceDefinition>> decidingTogether(String organizationId,
                                                              String applicationId,
                                                              Set<String> types,
                                                              Set<String> permissions,
                                                              Set<String> read,
                                                              Map<String, ServiceDefinition> found) {
        Set<String> unread = new TreeSet<>(types);
        unread.removeAll(read);
        read.addAll(unread);
        Set<String> parents = new TreeSet<>(unread);
        parents.removeAll(AuthzModelGenerator.APPLICATION_TYPES);
        return entries.findEntriesNaming(organizationId, applicationId, unread, parents, permissions).compose(naming -> {
            Set<String> further = new TreeSet<>();
            for (ServiceDirectoryRecord entry : naming) {
                if (found.putIfAbsent(entry.getId(), entry.getServiceDefinition()) == null) {
                    further.addAll(generator.namesOf(entry.getServiceDefinition()).named());
                }
            }
            further.removeAll(read);
            return further.isEmpty()
                    ? Future.succeededFuture(List.copyOf(found.values()))
                    : decidingTogether(organizationId, applicationId, further, Set.of(), read, found);
        });
    }

    // Of the system's services an application's model takes those declared on the tenant and on the entity
    // definition, which decide the type
    private Future<List<ServiceDefinition>> systemDefinitions() {
        return entries.findEntriesNaming(null, null, List.of(AuthzConstants.TENANT_TYPE, AuthzConstants.ENTITY_DEFINITION_TYPE), List.of(), List.of())
                      .map(naming -> naming.stream().map(ServiceDirectoryRecord::getServiceDefinition).toList());
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
