package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.idl.api.schema.ServiceDefinition;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import io.vertx.core.shareddata.SharedData;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.participant.*;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.services.security.authorization.AccessControlService;
import org.kinotic.domain.internal.api.repositories.*;
import org.kinotic.domain.internal.model.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Supplier;

/** Source policies stay in Elasticsearch. OpenFGA contains only their effective permission projection. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class DefaultAccessControlService implements AccessControlService {
    private final SecurityContext securityContext;
    private final DefaultPermissionAuthorizationService authorization;
    private final PermissionCatalogService catalog;
    private final AuthorizationPolicyValidator validator;
    private final AuthorizationPolicyRepository policies;
    private final AuthorizationModelRepository models;
    private final AuthorizationProjectionRepository projections;
    private final ApplicationRepository applications;
    private final OpenFgaClient client;
    private final SharedData sharedData;
    private final Vertx vertx;
    private final ServiceDirectoryEntryRepository directory;
    private final ParticipantIdentityRepository identities;

    @Override
    public Future<AuthorizationPolicyView> load(AuthorizationScope scope) {
        return authorizeAdministration(scope).compose(ignored -> view(scope));
    }
    @Override
    public Future<AuthorizationPolicyView> initialize(AuthorizationScope scope, String administratorIdentityId) {
        var actor = securityContext.requireParticipant(ScopedParticipant.class);
        if (scope.getKind() == AuthorizationScopeKind.ORGANIZATION && !authorization.bootstrap(actor)) return Future.failedFuture(new AuthorizationException("Organization initialization requires a configured operator"));
        return mutate(scope, actor, () -> policies.findById(scope.key()).compose(existing -> {
            if (existing != null) return Future.failedFuture(new IllegalStateException("Access is already initialized"));
            var policy = new AuthorizationPolicy().setScope(scope).setId(scope.key()).setRevision(1).setAdministrators(List.of(administratorIdentityId));
            return catalog.permissions(scope).compose(permissions -> validator.validate(policy, permissions)
                    .compose(valid -> publish(policy, permissions))).compose(published -> view(scope));
        }));
    }
    @Override
    public Future<AuthorizationPolicyView> save(AuthorizationPolicy policy, long expectedRevision) {
        var scope = policy.getScope();
        var actor = securityContext.requireParticipant(ScopedParticipant.class);
        return mutate(scope, actor, () -> policies.findById(scope.key()).compose(existing -> {
            if (existing == null || existing.getRevision() != expectedRevision) return Future.failedFuture(new IllegalStateException("Policy revision conflict; reload before saving"));
            policy.setId(scope.key()).setRevision(expectedRevision + 1);
            return catalog.permissions(scope).compose(permissions -> validator.validate(policy, permissions)
                    .compose(valid -> publish(policy, permissions))).compose(published -> view(scope));
        }));
    }
    @Override
    public Future<AuthorizationPolicyView> republish(AuthorizationScope scope, long expectedRevision) {
        var actor = securityContext.requireParticipant(ScopedParticipant.class);
        return mutate(scope, actor, () -> policies.findById(scope.key()).compose(policy -> {
            if (policy == null || policy.getRevision() != expectedRevision) return Future.failedFuture(new IllegalStateException("Policy revision conflict"));
            return catalog.permissions(scope).compose(permissions -> validator.validate(policy, permissions)
                    .compose(valid -> publish(policy, permissions))).compose(published -> view(scope));
        }));
    }

    @Override
    public Future<org.kinotic.core.api.crud.Page<AuthorizationIdentityOption>> findIdentities(AuthorizationScope scope, Pageable pageable) {
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 200) return Future.failedFuture(new IllegalArgumentException("Page size must be 1..200"));
        return authorizeAdministration(scope).compose(ignored -> identities.findForAuthorizationScope(scope, pageable))
                .map(page -> page.map(identity -> new AuthorizationIdentityOption(identity.getId(), identity.getDisplayName() == null ? identity.getId() : identity.getDisplayName())));
    }

    @Override
    public Future<Void> publishContracts(String applicationId, List<ApplicationServiceContract> contracts) {
        var actor = securityContext.requireParticipant(ScopedParticipant.class);
        if (!(actor instanceof OrganizationParticipant)) return denied();
        var scope = new AuthorizationScope().setKind(AuthorizationScopeKind.APPLICATION)
                .setOrganizationId(actor.getScope().organizationId()).setApplicationId(applicationId);
        var entries = validateContracts(scope, contracts);
        return mutate(scope, actor, () -> models.findById(scope.key()).compose(existing -> {
            var model = existing == null ? new AuthorizationModel().setId(scope.key()) : existing;
            return models.saveSync(model.setPending(true).setRevision(model.getRevision() + 1)).compose(fenced -> {
                Future<Void> writes = Future.succeededFuture();
                for (var entry : entries) writes = writes.compose(done -> directory.upsertEntry(entry));
                return writes.compose(done -> republishApplication(scope, null)).compose(done -> models.findById(scope.key()))
                        .compose(current -> models.saveSync(current.setPending(false))).mapEmpty();
            });
        }));
    }

    private List<ServiceDirectoryEntry> validateContracts(AuthorizationScope scope, List<ApplicationServiceContract> contracts) {
        if (contracts == null || contracts.size() > 200) throw new IllegalArgumentException("Invalid contract batch");
        var entries = new ArrayList<ServiceDirectoryEntry>();
        var addresses = new HashSet<String>();
        for (var contract : contracts) {
            if (contract.getName() == null || !contract.getName().matches("[A-Za-z_$][A-Za-z0-9_$]*")
                    || contract.getNamespace() != null && !contract.getNamespace().matches("[A-Za-z_$][A-Za-z0-9_$.]*")
                    || contract.getVersion() == null || !contract.getVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9]+)?")
                    || contract.getZone() != null && !contract.getZone().matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")
                    || contract.getFunctions() == null || contract.getFunctions().size() > 500) throw new IllegalArgumentException("Invalid service contract");
            var names = new HashSet<String>();
            for (var function : contract.getFunctions()) {
                if (function.getName() == null || !names.add(function.getName())) throw new IllegalArgumentException("Invalid function name");
                var declaration = function.findDecorator(RequirePermissionC3Decorator.class);
                if (declaration == null) continue;
                AuthorizationCompiler.validatePermission(new AuthorizationPermission().setPermission(declaration.getPermission()).setResourceType(declaration.getResourceType()));
                if (declaration.getIdArgument() != null && !declaration.getIdArgument().isEmpty()) {
                    String root = declaration.getIdArgument().split("\\.")[0];
                    int index = -1;
                    for (int position = 0; position < function.getParameters().size(); position++) if (function.getParameters().get(position).getName().equals(root)) index = position;
                    if (index < 0) throw new IllegalArgumentException("Unknown resource argument");
                    declaration.setArgumentIndex(index);
                }
            }
            String zone = "app." + scope.getOrganizationId() + "." + scope.getApplicationId() + (contract.getZone() == null ? "" : "." + contract.getZone());
            String qualified = contract.getNamespace() == null ? contract.getName() : contract.getNamespace() + "." + contract.getName();
            String id = zone + "~" + qualified;
            if (!addresses.add(id)) throw new IllegalArgumentException("Duplicate service contract");
            entries.add(new ServiceDirectoryEntry().setId(id).setName(contract.getName()).setNamespace(contract.getNamespace())
                    .setZone(zone).setVersion(contract.getVersion()).setOrganizationId(scope.getOrganizationId()).setApplicationId(scope.getApplicationId())
                    .setServiceDefinition(new ServiceDefinition().setName(contract.getName()).setNamespace(contract.getNamespace()).setFunctions(new LinkedHashSet<>(contract.getFunctions()))));
        }
        return entries;
    }

    private Future<Void> republishApplication(AuthorizationScope scope, String cursor) {
        return policies.findForApplication(scope.getOrganizationId(), scope.getApplicationId(), Pageable.create(cursor, 100, null)).compose(page -> {
            Future<Void> writes = Future.succeededFuture();
            for (var policy : page.getContent()) writes = writes.compose(ignored -> catalog.permissions(policy.getScope())
                    .compose(permissions -> validator.validate(policy, permissions).compose(valid -> publish(policy, permissions, false))));
            String next = page instanceof CursorPage<?> cp ? cp.getCursor() : null;
            return writes.compose(ignored -> next == null || page.getContent().isEmpty() ? Future.succeededFuture() : republishApplication(scope, next));
        });
    }

    private Future<Void> authorizeAdministration(AuthorizationScope scope) {
        return authorizeAdministration(scope, securityContext.requireParticipant(ScopedParticipant.class));
    }
    private Future<Void> authorizeAdministration(AuthorizationScope scope, ScopedParticipant actor) {
        scope.key();
        if (authorization.bootstrap(actor)) return requireApplication(scope);
        if (!Objects.equals(actor.getScope().organizationId(), scope.getOrganizationId())) return denied();
        var own = DefaultPermissionAuthorizationService.scope(actor);
        if (actor instanceof OrganizationParticipant) {
            var permission = scope.getKind() == AuthorizationScopeKind.ORGANIZATION
                    ? authorization.require(actor, own, "authorization", null, "access.manage")
                    : authorization.require(actor, own, "application", scope.getApplicationId(), "applications.access.manage");
            return permission.compose(ignored -> requireApplication(scope));
        }
        if (!(actor instanceof ApplicationParticipant) || !Objects.equals(own.getApplicationId(), scope.getApplicationId())
                || scope.getKind() == AuthorizationScopeKind.ORGANIZATION
                || (own.getTenantId() != null && !Objects.equals(own.getTenantId(), scope.getTenantId()))) return denied();
        return authorization.require(actor, own, "authorization", null, "access.manage");
    }
    private Future<Void> requireApplication(AuthorizationScope scope) {
        return scope.getApplicationId() == null ? Future.succeededFuture()
                : applications.requireById(scope.getApplicationId(), scope.getOrganizationId()).mapEmpty();
    }
    private Future<Void> denied() { return Future.failedFuture(new AuthorizationException("Access denied")); }

    private <T> Future<T> mutate(AuthorizationScope scope, ScopedParticipant actor, Supplier<Future<T>> work) {
        return authorizeAdministration(scope, actor).compose(ignored -> locked(scope,
                () -> authorizeAdministration(scope, actor).compose(authorized -> work.get())));
    }
    private <T> Future<T> locked(AuthorizationScope scope, Supplier<Future<T>> work) {
        return sharedData.getLockWithTimeout("kinotic.authorization." + scope.applicationScope().key(), 1000).compose(lock -> {
            Future<T> future;
            try { future = work.get(); } catch (Exception e) { lock.release(); return Future.failedFuture(e); }
            return future.onComplete(ignored -> lock.release());
        });
    }

    private Future<Void> publish(AuthorizationPolicy policy, List<AuthorizationPermission> permissions) {
        return models.findById(policy.getScope().applicationScope().key()).compose(model -> {
            boolean repair = model != null && model.isPending();
            return publish(policy, permissions, !repair).compose(ignored -> {
                if (!repair) return Future.succeededFuture();
                return republishApplication(policy.getScope().applicationScope(), null)
                        .compose(done -> models.findById(policy.getScope().applicationScope().key()))
                        .compose(current -> models.saveSync(current.setPending(false))).mapEmpty();
            });
        });
    }
    private Future<Void> publish(AuthorizationPolicy policy, List<AuthorizationPermission> permissions, boolean activate) {
        String modelKey = policy.getScope().applicationScope().key();
        return vertx.executeBlocking(() -> AuthorizationCompiler.project(policy, permissions), false).compose(desired -> models.findById(modelKey).compose(existing -> {
            var model = existing == null ? new AuthorizationModel().setId(modelKey) : existing;
            model.setPending(true).setRevision(model.getRevision() + 1);
            return models.saveSync(model).compose(fenced -> ensureModel(model, permissions))
                    .compose(ready -> projections.findById(policy.getId()))
                    .compose(previous -> {
                        var candidates = new LinkedHashSet<AuthorizationTuple>(desired);
                        if (previous != null) { candidates.addAll(previous.getTuples()); candidates.addAll(previous.getCandidates()); }
                        var projection = new AuthorizationProjection().setId(policy.getId()).setTuples(desired).setCandidates(List.copyOf(candidates));
                        return projections.saveSync(projection).compose(staged -> policies.saveSync(policy))
                                .compose(saved -> client.reconcile(model.getStoreId(), model.getModelId(), projection.getCandidates(), desired))
                                .compose(reconciled -> projections.saveSync(projection.setCandidates(List.of()))).mapEmpty();
                    }).compose(done -> models.saveSync(model.setPending(!activate))).mapEmpty();
        }));
    }

    private Future<AuthorizationModel> ensureModel(AuthorizationModel model, List<AuthorizationPermission> permissions) {
        Future<String> store = model.getStoreId() == null ? client.createStore("Kinotic authorization") : Future.succeededFuture(model.getStoreId());
        return store.compose(storeId -> {
            model.setStoreId(storeId);
            return models.saveSync(model).compose(saved -> {
                var types = new TreeSet<>(model.getResourceTypes()); permissions.forEach(permission -> types.add(permission.getResourceType()));
                if (model.getModelId() != null && types.equals(new TreeSet<>(model.getResourceTypes()))) return Future.succeededFuture(model);
                return client.writeModel(storeId, AuthorizationCompiler.model(types)).compose(modelId -> models.saveSync(model.setModelId(modelId).setResourceTypes(List.copyOf(types))));
            });
        });
    }
    private Future<AuthorizationPolicyView> view(AuthorizationScope scope) {
        return policies.findById(scope.key()).compose(policy -> catalog.permissions(scope).compose(permissions -> models.findById(scope.applicationScope().key())
                .map(model -> new AuthorizationPolicyView().setPolicy(policy).setPermissions(permissions).setPending(model != null && model.isPending())
                        .setModelRevision(model == null ? 0 : model.getRevision()))));
    }
}
