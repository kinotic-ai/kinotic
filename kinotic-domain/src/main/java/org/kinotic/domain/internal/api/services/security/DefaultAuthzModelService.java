package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.ServiceModelNames;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.internal.api.model.ServiceDirectoryRecord;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

@Slf4j
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
    public Future<Set<String>> writeContracts(Collection<ServiceDirectoryEntry> written, Supplier<Future<Set<String>>> write) {
        List<String> ids = written.stream().map(ServiceDirectoryEntry::getId).toList();
        // what each entry's stored contract names, which the contract about to be written may have left
        return entries.findAuthzNames(ids).compose(replaced -> generable(written, replaced).compose(
                decided -> write.get()
                                .onSuccess(changed -> announce(written, changed, replaced, decided))
                                // a write failing for one entry has stored the others and names none of them,
                                // so every entry is taken as written
                                .onFailure(e -> announce(written, Set.copyOf(ids), replaced, decided))));
    }

    // A declaration the derivation accepts can still give the generator nothing to build: a role bundling a
    // permission no function requires, a parent forking the type tree. Stored, it would leave the types it names
    // without a model, so the generator is asked first and its refusal fails the registration. A model is
    // generated from every service deciding a type, so a batch's entries of one application are tried together,
    // as a role one declares may bundle a permission another requires, with the types their stored contracts
    // name. The system's own contracts are generated from at startup, so only an application's are tried here.
    // Answers the types of each application's trial that have permissions, by its store
    private Future<Map<String, Set<String>>> generable(Collection<ServiceDirectoryEntry> written, Map<String, ServiceModelNames> replaced) {
        Map<String, List<ServiceDirectoryEntry>> byApplication = new HashMap<>();
        for (ServiceDirectoryEntry entry : written) {
            if (entry.getApplicationId() != null) {
                byApplication.computeIfAbsent(AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId()),
                                              k -> new ArrayList<>()).add(entry);
            }
        }
        Map<String, Set<String>> ret = new HashMap<>();
        List<Future<?>> trials = new ArrayList<>();
        byApplication.forEach((store, candidates) -> {
            ServiceDirectoryEntry application = candidates.getFirst();
            Set<String> left = new TreeSet<>();
            for (ServiceDirectoryEntry candidate : candidates) {
                ServiceModelNames stored = replaced.get(candidate.getId());
                if (stored != null) {
                    left.addAll(stored.named());
                }
            }
            trials.add(partialModel(application.getOrganizationId(), application.getApplicationId(), left, List.of(),
                                    candidates.stream().map(ServiceDirectoryEntry::getServiceDefinition).toList())
                               .onSuccess(model -> ret.put(store, model.permissions().keySet())));
        });
        return Future.all(trials).map(ret);
    }

    // The entries are stored whether or not a store's renewal lands; a store left behind is regenerated when the
    // reconcile master next starts, which reconciles every store. An application's store runs the same model
    // whatever services of its own types the application has, so it is renewed only for a contract that decides a
    // type the store places or decided one before; every application's model includes system services, so a
    // changed system contract renews every store. The types a written contract decides, with those decided with
    // them, are announced for the nodes keeping what was read of them
    private void announce(Collection<ServiceDirectoryEntry> written,
                          Set<String> changed,
                          Map<String, ServiceModelNames> replaced,
                          Map<String, Set<String>> decided) {
        Map<String, Set<String>> writtenByStore = new HashMap<>();
        boolean systemChanged = false;
        for (ServiceDirectoryEntry entry : written) {
            if (changed.contains(entry.getId())) {
                if (entry.getApplicationId() == null) {
                    systemChanged = true;
                } else {
                    Set<String> types = writtenByStore.computeIfAbsent(AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId()),
                                                                       k -> new TreeSet<>());
                    types.addAll(generator.namesOf(entry.getServiceDefinition()).types());
                    ServiceModelNames stored = replaced.get(entry.getId());
                    if (stored != null) {
                        types.addAll(stored.types());
                    }
                }
            }
        }
        boolean renewAll = systemChanged;
        writtenByStore.forEach((store, named) -> {
            Set<String> types = new TreeSet<>(named);
            types.addAll(decided.getOrDefault(store, Set.of()));
            vertx.eventBus().publish(TYPES_CHANGED_ADDRESS,
                                     new JsonObject().put("store", store).put("types", new JsonArray(List.copyOf(types))));
            if (!renewAll && !Collections.disjoint(named, AuthzModelGenerator.APPLICATION_TYPES)) {
                stores.renewDesired(store, "service directory")
                      .onFailure(e -> log.error("Could not renew the authorization store {} after its contracts changed", store, e));
            }
        });
        if (renewAll) {
            stores.renewAllDesired("service directory")
                  .onFailure(e -> log.error("Could not renew the authorization stores after the system's contracts changed", e));
        }
    }

    // The model an application's store would run as far as the given types and permissions go, with each candidate
    // standing in for the service of the same qualified name the directory holds
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
