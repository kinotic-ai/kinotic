package org.kinotic.domain.internal.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.ServiceModelNames;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.directory.ServiceDirectoryStrategy;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch. An application's contract is
 * stored only once a model can be generated from it and the services deciding the same types, so a registration
 * fails with the reason instead of leaving the application with declarations no model can be made of. A written
 * contract that decides a type its application's store places renews the intent of that store, and a written system
 * contract the intent of every store, so the store's model is generated again from the directory. The types a
 * written contract of an application decides are announced on {@link AuthzModelService#TYPES_CHANGED_ADDRESS}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;
    private final AuthzStoreRepository authzStores;
    // the model service reads the directory this strategy backs, so it is resolved once both exist
    private final ObjectProvider<AuthzModelService> models;
    private final AuthzModelGenerator generator;
    private final Vertx vertx;

    @Override
    public Future<Set<String>> upsertEntry(ServiceDirectoryEntry entry) {
        return upsertEntries(List.of(entry));
    }

    @Override
    public Future<Set<String>> upsertEntries(Collection<ServiceDirectoryEntry> entries) {
        List<String> ids = entries.stream().map(ServiceDirectoryEntry::getId).toList();
        // what each entry's stored contract names, which the contract about to be written may have left
        return repository.findAuthzNames(ids).compose(replaced -> generable(entries, replaced).compose(
                decided -> repository.upsertEntries(entries)
                                     .onSuccess(changed -> announce(entries, changed, replaced, decided))
                                     // a write failing for one entry has stored the others and names none of
                                     // them, so every entry is taken as written
                                     .onFailure(e -> announce(entries, Set.copyOf(ids), replaced, decided))));
    }

    // A declaration the derivation accepts can still give the generator nothing to build: a role bundling a
    // permission no function requires, a parent forking the type tree. Stored, it would leave the types it names
    // without a model, so the generator is asked first and its refusal fails the registration. A model is
    // generated from every service deciding a type, so a batch's entries of one application are tried together,
    // as a role one declares may bundle a permission another requires, with the types their stored contracts
    // name. The system's own contracts are generated from at startup, so only an application's are tried here.
    // Answers the types of each application's trial that have permissions, by its store
    private Future<Map<String, Set<String>>> generable(Collection<ServiceDirectoryEntry> entries, Map<String, ServiceModelNames> replaced) {
        Map<String, List<ServiceDirectoryEntry>> byApplication = new HashMap<>();
        for (ServiceDirectoryEntry entry : entries) {
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
            trials.add(models.getObject()
                             .findApplicationModel(application.getOrganizationId(), application.getApplicationId(),
                                                   candidates.stream().map(ServiceDirectoryEntry::getServiceDefinition).toList(), left)
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
    private void announce(Collection<ServiceDirectoryEntry> entries,
                          Set<String> changed,
                          Map<String, ServiceModelNames> replaced,
                          Map<String, Set<String>> decided) {
        Map<String, Set<String>> writtenByStore = new HashMap<>();
        boolean systemChanged = false;
        for (ServiceDirectoryEntry entry : entries) {
            if (changed.contains(entry.getId())) {
                if (entry.getApplicationId() == null) {
                    systemChanged = true;
                } else {
                    Set<String> written = writtenByStore.computeIfAbsent(AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId()),
                                                                         k -> new TreeSet<>());
                    written.addAll(generator.namesOf(entry.getServiceDefinition()).types());
                    ServiceModelNames stored = replaced.get(entry.getId());
                    if (stored != null) {
                        written.addAll(stored.types());
                    }
                }
            }
        }
        boolean renewAll = systemChanged;
        writtenByStore.forEach((store, written) -> {
            Set<String> types = new TreeSet<>(written);
            types.addAll(decided.getOrDefault(store, Set.of()));
            vertx.eventBus().publish(AuthzModelService.TYPES_CHANGED_ADDRESS,
                                     new JsonObject().put("store", store).put("types", new JsonArray(List.copyOf(types))));
            if (!renewAll && !Collections.disjoint(written, AuthzModelGenerator.APPLICATION_TYPES)) {
                authzStores.renewDesired(store, "service directory")
                           .onFailure(e -> log.error("Could not renew the authorization store {} after its contracts changed", store, e));
            }
        });
        if (renewAll) {
            authzStores.renewAllDesired("service directory")
                       .onFailure(e -> log.error("Could not renew the authorization stores after the system's contracts changed", e));
        }
    }

    @Override
    public Future<Void> setOnline(String entryId, boolean online, Instant when) {
        return repository.setOnline(entryId, online, when);
    }

    @Override
    public Future<Void> setOnline(Map<String, Boolean> onlineByEntryId, Instant when) {
        return repository.setOnline(onlineByEntryId, when);
    }

    @Override
    public Future<Void> setOnlineByAddress(String serviceAddress, boolean online, Instant when) {
        return repository.setOnlineByAddress(serviceAddress, online, when);
    }

    @Override
    public Future<Void> reconcileLiveness(Set<String> activeAddresses, Instant when) {
        return repository.reconcileLiveness(activeAddresses, when);
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findEntriesScopedTo(String organizationId,
                                                                   String applicationId,
                                                                   Pageable pageable) {
        return repository.findEntriesScopedTo(organizationId, applicationId, pageable);
    }

    @Override
    public Future<Page<ServiceDirectoryEntry>> findSystemEntries(Pageable pageable) {
        return repository.findSystemEntries(pageable);
    }

    @Override
    public Future<ServiceDirectoryEntry> findEntry(String entryId) {
        return repository.findById(entryId).map(ServiceDirectoryEntry.class::cast);
    }

    @Override
    public Future<McpToolDefinition> findMcpToolByName(String toolName,
                                                       String organizationId,
                                                       String applicationId) {
        return repository.findMcpToolByName(toolName, organizationId, applicationId);
    }

    @Override
    public Future<McpToolDefinitionList> findMcpToolsCallableBy(String organizationId,
                                                                String applicationId,
                                                                CursorPageable pageable) {
        return repository.findMcpToolsCallableBy(organizationId, applicationId, pageable);
    }

}
