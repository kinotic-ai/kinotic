package org.kinotic.persistence.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.model.persistence.*;
import org.kinotic.domain.api.model.persistence.idl.decorators.PolicyDecorator;
import org.kinotic.domain.api.model.persistence.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.services.security.authorization.PermissionAuthorizationService;
import org.kinotic.domain.api.model.security.authorization.AuthorizationScope;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.idl.api.schema.decorators.RequirePermissionC3Decorator;
import org.kinotic.persistence.api.model.*;
import org.kinotic.persistence.api.services.security.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Replaces the legacy persistence factory when gateway authorization is enabled. */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class OpenFgaAuthorizationServiceFactory implements AuthorizationServiceFactory {
    private final PermissionAuthorizationService authorization;
    private final SecurityContext securityContext;
    private final EntityDefinitionRepository entities;

    @Override
    public Future<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition definition) {
        if (legacyPolicies(definition)) return Future.failedFuture(new IllegalStateException("Migrate legacy Policy decorators before enabling OpenFGA"));
        return Future.succeededFuture((operation, context) -> {
            Future<Void> result = require(context, definition.toDescriptor(), "entity_definition", "data." + operation.methodName());
            var entityPermission = definition.getSchema().findDecorator(RequirePermissionC3Decorator.class);
            if (entityPermission != null) result = result.compose(ignored -> require(context, definition.toDescriptor(), entityPermission.getResourceType(), entityPermission.getPermission()));
            for (var property : definition.getDecoratedProperties()) {
                var permission = property.findDecorator(RequirePermissionC3Decorator.class);
                boolean included = !context.hasIncludedFieldsFilter() || context.getIncludedFieldsFilter().stream().anyMatch(field -> field.equals(property.getJsonPath()) || property.getJsonPath().startsWith(field + ".") || field.startsWith(property.getJsonPath() + "."));
                // Writes conservatively require every protected field; EntityContext carries no changed-field set.
                if (permission != null && (included || writes(operation))) result = result.compose(ignored -> require(context, definition.toDescriptor(), permission.getResourceType(), permission.getPermission()));
            }
            return result;
        });
    }
    @Override
    public Future<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition query) {
        return Future.failedFuture(new IllegalStateException("Named query authorization requires entity ownership metadata"));
    }
    @Override
    public Future<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition query, EntityDescriptor entity) {
        if (query.containsDecorator(PolicyDecorator.class)) return Future.failedFuture(new IllegalStateException("Migrate legacy named-query policies before enabling OpenFGA"));
        var permission = query.findDecorator(RequirePermissionC3Decorator.class);
        return Future.succeededFuture((operation, context) -> entities.findById(entity.id(), entity.organizationId()).compose(definition -> {
            if (definition == null || !definition.toDescriptor().equals(entity)) return denied();
            return require(context, entity, permission == null ? "entity_definition" : permission.getResourceType(), permission == null ? "queries.execute" : permission.getPermission());
        }));
    }
    private Future<Void> require(EntityContext context, EntityDescriptor entity, String type, String permission) {
        ScopedParticipant actor = securityContext.requireParticipant(ScopedParticipant.class);
        if (context.getParticipant() == null || !Objects.equals(context.getParticipant().getId(), actor.getId())
                || !Objects.equals(context.getParticipant().getScope(), actor.getScope())) return denied();
        if (actor == null || !Objects.equals(actor.getScope().organizationId(), entity.organizationId())
                || !Objects.equals(context.getOrganizationId(), entity.organizationId())
                || actor.getScope().applicationId() != null && !Objects.equals(actor.getScope().applicationId(), entity.applicationId())) return denied();
        String tenant = actor.getScope().tenantId();
        if (tenant != null && (!Objects.equals(tenant, context.getTenantId()) || context.hasTenantSelection() && context.getTenantSelection().stream().anyMatch(id -> !tenant.equals(id)))) return denied();
        return authorization.require(actor, AuthorizationScope.from(actor), type, entity.id(), permission);
    }
    private Future<Void> denied() { return Future.failedFuture(new AuthorizationException("Access denied")); }
    private boolean writes(EntityOperation operation) {
        return switch (operation) { case SAVE, UPDATE, BULK_SAVE, BULK_UPDATE, DELETE_BY_ID, DELETE_BY_QUERY -> true; default -> false; };
    }
    private boolean legacyPolicies(EntityDefinition definition) {
        if (definition.getSchema().containsDecorator(PolicyDecorator.class) || definition.getDecoratedProperties().stream().anyMatch(p -> p.findDecorator(PolicyDecorator.class) != null)) return true;
        var operations = definition.getSchema().findDecorator(EntityServiceDecoratorsDecorator.class);
        return operations != null && operations.getConfig().getOperationDecoratorMap().values().stream().flatMap(java.util.Collection::stream).anyMatch(PolicyDecorator.class::isInstance);
    }
}
