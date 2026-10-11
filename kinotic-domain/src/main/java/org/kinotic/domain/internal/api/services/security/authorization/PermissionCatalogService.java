package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.domain.api.model.persistence.EntityOperation;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.internal.api.repositories.ServiceDirectoryEntryRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.repositories.NamedQueriesDefinitionRepository;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;

/** Reads developer-declared capabilities for the UI and projection; never loads roles during a check. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class PermissionCatalogService {
    private final ServiceDirectoryEntryRepository directory;
    private final EntityDefinitionRepository entities;
    private final NamedQueriesDefinitionRepository queries;

    public Future<List<AuthorizationPermission>> permissions(AuthorizationScope scope) {
        var permissions = new LinkedHashMap<String, AuthorizationPermission>();
        add(permissions, new AuthorizationPermission().setPermission("access.manage").setResourceType("authorization")
                .setLabel("Manage access").setTenantDelegable(true));
        if (scope.getKind() == AuthorizationScopeKind.ORGANIZATION) {
            add(permissions, new AuthorizationPermission().setPermission("applications.access.manage").setResourceType("application")
                    .setLabel("Manage application access"));
        }
        for (var operation : EntityOperation.values()) {
            add(permissions, new AuthorizationPermission().setPermission("data." + operation.methodName())
                    .setResourceType("entity_definition").setLabel(operation.methodName()).setTenantDelegable(true));
        }
        return collect(scope, null, permissions).compose(ignored -> collectData(scope, null, permissions)).map(ignored -> permissions.values().stream()
                .filter(p -> scope.getKind() != AuthorizationScopeKind.APPLICATION_TENANT || p.isTenantDelegable()).toList());
    }

    private Future<Void> collect(AuthorizationScope scope, String cursor, Map<String, AuthorizationPermission> permissions) {
        return directory.findAll(Pageable.create(cursor, 200, null)).compose(page -> {
            for (var entry : page.getContent()) {
                if (!visible(scope, entry) || entry.getServiceDefinition() == null) continue;
                for (var function : entry.getServiceDefinition().getFunctions()) {
                    var declaration = function.findDecorator(RequirePermissionC3Decorator.class);
                    if (declaration != null) add(permissions, new AuthorizationPermission().setPermission(declaration.getPermission())
                            .setResourceType(declaration.getResourceType()).setLabel(declaration.getLabel())
                            .setTenantDelegable(declaration.isTenantDelegable()));
                }
            }
            if (permissions.size() > 10_000) return Future.failedFuture(new IllegalArgumentException("Permission catalog exceeds 10000 entries"));
            String next = page instanceof CursorPage<?> cp ? cp.getCursor() : null;
            return next == null || page.getContent().isEmpty() ? Future.succeededFuture() : collect(scope, next, permissions);
        });
    }

    private Future<Void> collectData(AuthorizationScope scope, String cursor, Map<String, AuthorizationPermission> permissions) {
        return entities.findAll(scope.getOrganizationId(), Pageable.create(cursor, 200, null)).compose(page -> {
            Future<Void> reads = Future.succeededFuture();
            for (var entity : page.getContent()) {
                if (!entity.isPublished() || scope.getApplicationId() != null && !scope.getApplicationId().equals(entity.getApplicationId())) continue;
                addDeclaration(permissions, entity.getSchema().findDecorator(RequirePermissionC3Decorator.class));
                for (var property : entity.getDecoratedProperties()) addDeclaration(permissions, property.findDecorator(RequirePermissionC3Decorator.class));
                reads = reads.compose(ignored -> queries.findByApplicationAndEntityDefinition(entity.getApplicationId(), entity.getName(), scope.getOrganizationId()).map(named -> {
                    if (named != null && named.getNamedQueries() != null) for (var query : named.getNamedQueries()) addDeclaration(permissions, query.findDecorator(RequirePermissionC3Decorator.class));
                    return null;
                }));
            }
            String next = page instanceof CursorPage<?> cp ? cp.getCursor() : null;
            return reads.compose(ignored -> next == null || page.getContent().isEmpty() ? Future.succeededFuture() : collectData(scope, next, permissions));
        });
    }
    private void addDeclaration(Map<String, AuthorizationPermission> permissions, RequirePermissionC3Decorator declaration) {
        if (declaration != null) add(permissions, new AuthorizationPermission().setPermission(declaration.getPermission()).setResourceType(declaration.getResourceType())
                .setLabel(declaration.getLabel()).setTenantDelegable(declaration.isTenantDelegable()));
        if (permissions.size() > 10_000) throw new IllegalArgumentException("Permission catalog exceeds 10000 entries");
    }

    private boolean visible(AuthorizationScope scope, ServiceDirectoryEntry entry) {
        if (entry.getOrganizationId() == null) {
            return "app-api".equals(entry.getZone()) || (scope.getKind() == AuthorizationScopeKind.ORGANIZATION && "management-api".equals(entry.getZone()));
        }
        return scope.getKind() != AuthorizationScopeKind.ORGANIZATION && Objects.equals(scope.getOrganizationId(), entry.getOrganizationId())
                && Objects.equals(scope.getApplicationId(), entry.getApplicationId());
    }

    private void add(Map<String, AuthorizationPermission> permissions, AuthorizationPermission permission) {
        AuthorizationCompiler.validatePermission(permission);
        String key = permission.getResourceType() + ":" + permission.getPermission();
        var existing = permissions.putIfAbsent(key, permission);
        if (existing != null && existing.isTenantDelegable() != permission.isTenantDelegable()) {
            throw new IllegalArgumentException("Conflicting tenant delegation for " + permission.getPermission());
        }
    }
}
