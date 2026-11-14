package org.kinotic.domain.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link ServiceDirectoryStrategy} persisting directory entries in Elasticsearch. An application's contract is
 * stored only once its store's model can be generated with it, so a registration fails with the reason instead of
 * leaving the store on a model the directory no longer implies. A written contract renews the intent of the
 * authorization store the entry belongs to, or of every store for a system contract, so the store's model is
 * generated again from the directory.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticServiceDirectoryStrategy implements ServiceDirectoryStrategy {

    private final ServiceDirectoryEntryRepository repository;
    private final AuthzStoreRepository authzStores;
    // the model service reads the directory this strategy backs, so it is resolved once both exist
    private final ObjectProvider<AuthzModelService> models;

    @Override
    public Future<Set<String>> upsertEntry(ServiceDirectoryEntry entry) {
        return upsertEntries(List.of(entry));
    }

    @Override
    public Future<Set<String>> upsertEntries(Collection<ServiceDirectoryEntry> entries) {
        return generable(entries).compose(v -> repository.upsertEntries(entries)
                                                         .onSuccess(changed -> renewStoresOf(entries, changed))
                                                         // a write failing for one entry has stored the others and
                                                         // names none of them, so every entry's store is renewed
                                                         .onFailure(e -> renewStoresOf(entries, entries.stream()
                                                                                                       .map(ServiceDirectoryEntry::getId)
                                                                                                       .collect(Collectors.toSet()))));
    }

    // A declaration the derivation accepts can still give the generator nothing to build: a role bundling a
    // permission no function requires, a parent forking the type tree. Stored, it would fail the store's every
    // reconcile until replaced, so the generator is asked first and its refusal fails the registration. The
    // reconciler generates a store's model from all of its services at once, so a batch's entries of one
    // application are tried together, as a role one declares may bundle a permission another requires. The
    // system's own contracts are generated from at startup, so only an application's are tried here
    private Future<Void> generable(Collection<ServiceDirectoryEntry> entries) {
        Map<String, List<ServiceDefinition>> byApplication = new HashMap<>();
        Map<String, ServiceDirectoryEntry> applications = new HashMap<>();
        for (ServiceDirectoryEntry entry : entries) {
            if (entry.getApplicationId() != null) {
                String store = AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId());
                byApplication.computeIfAbsent(store, k -> new ArrayList<>()).add(entry.getServiceDefinition());
                applications.putIfAbsent(store, entry);
            }
        }
        List<Future<?>> trials = new ArrayList<>();
        for (Map.Entry<String, List<ServiceDefinition>> candidates : byApplication.entrySet()) {
            ServiceDirectoryEntry application = applications.get(candidates.getKey());
            trials.add(models.getObject().findApplicationModel(application.getOrganizationId(), application.getApplicationId(), candidates.getValue()));
        }
        return Future.all(trials).mapEmpty();
    }

    // The entries are stored whether or not the store's renewal lands; a store left behind is regenerated when
    // the reconcile master next starts, which reconciles every store. Every application's model includes system
    // services, so a changed system contract renews every store
    private void renewStoresOf(Collection<ServiceDirectoryEntry> entries, Set<String> changed) {
        Set<String> stores = new HashSet<>();
        boolean systemChanged = false;
        for (ServiceDirectoryEntry entry : entries) {
            if (changed.contains(entry.getId())) {
                if (entry.getApplicationId() == null) {
                    systemChanged = true;
                } else {
                    stores.add(AuthzGraphUtil.authzApplicationId(entry.getOrganizationId(), entry.getApplicationId()));
                }
            }
        }
        if (systemChanged) {
            authzStores.renewAllDesired("service directory")
                       .onFailure(e -> log.error("Could not renew the authorization stores after the system's contracts changed", e));
        } else {
            for (String store : stores) {
                authzStores.renewDesired(store, "service directory")
                           .onFailure(e -> log.error("Could not renew the authorization store {} after its contracts changed", store, e));
            }
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
