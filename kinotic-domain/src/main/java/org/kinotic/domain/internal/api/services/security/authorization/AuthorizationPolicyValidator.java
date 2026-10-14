package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentityType;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class AuthorizationPolicyValidator {
    private final ParticipantIdentityRepository identities;
    private final List<org.kinotic.domain.api.services.security.authorization.AuthorizationResourceResolver> resourceResolvers;

    public Future<Void> validate(AuthorizationPolicy policy, List<AuthorizationPermission> catalog) {
        try {
            policy.getScope().key();
            requireList(policy.getAdministrators(), 100); requireList(policy.getRoles(), 200);
            requireList(policy.getGroups(), 500); requireList(policy.getAssignments(), 2000);
            if (policy.getAdministrators().isEmpty()) throw new IllegalArgumentException("At least one administrator is required");
            var roleIds = new HashSet<String>(); var groupIds = new HashSet<String>(); var assignmentIds = new HashSet<String>();
            var subjects = new HashSet<>(policy.getAdministrators());
            int totalPatterns = 0;
            for (var role : policy.getRoles()) {
                unique(role.getId(), roleIds); requireList(role.getPermissions(), 1000);
                totalPatterns += role.getPermissions().size();
                if (totalPatterns > 2000) throw new IllegalArgumentException("Policy exceeds 2000 permission patterns");
                for (var pattern : role.getPermissions()) {
                    if (pattern == null || !pattern.matches("\\*|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*(\\.\\*)?")) throw new IllegalArgumentException("Invalid permission pattern");
                    if (catalog.stream().noneMatch(p -> AuthorizationCompiler.matches(pattern, p.getPermission()))) throw new IllegalArgumentException("Unknown or non-delegable permission: " + pattern);
                }
            }
            for (var group : policy.getGroups()) { unique(group.getId(), groupIds); requireList(group.getMemberIds(), 2000); subjects.addAll(group.getMemberIds()); }
            for (var assignment : policy.getAssignments()) {
                unique(assignment.getId(), assignmentIds);
                if (!roleIds.contains(assignment.getRoleId()) || assignment.getSubjectKind() == null || assignment.getSelector() == null || assignment.getEffect() == null) throw new IllegalArgumentException("Invalid assignment");
                if (assignment.getSubjectKind() == AuthorizationSubjectKind.IDENTITY) subjects.add(assignment.getSubjectId());
                else if (!groupIds.contains(assignment.getSubjectId())) throw new IllegalArgumentException("Unknown group");
                if (assignment.getSelector() == AuthorizationSelector.EXACT) identifier(assignment.getResourceId());
                else if (assignment.getResourceId() != null) throw new IllegalArgumentException("ALL assignment cannot specify a resource id");
                var role = policy.getRoles().stream().filter(v -> v.getId().equals(assignment.getRoleId())).findFirst().orElseThrow();
                if (catalog.stream().noneMatch(p -> p.getResourceType().equals(assignment.getResourceType()) && role.getPermissions().stream().anyMatch(pattern -> AuthorizationCompiler.matches(pattern, p.getPermission())))) throw new IllegalArgumentException("Role does not grant permissions for this resource type");
            }
            if (subjects.size() > 10000) throw new IllegalArgumentException("Policy exceeds 10000 distinct identity subjects");
            Future<Void> result = Future.succeededFuture();
            for (var assignment : policy.getAssignments()) {
                if (assignment.getSelector() != AuthorizationSelector.EXACT) continue;
                var resolver = resourceResolvers.stream().filter(value -> value.supports(assignment.getResourceType())).findFirst();
                if (resolver.isPresent()) result = result.compose(ignored -> resolver.get().requireOwned(assignment.getResourceType(), assignment.getResourceId(), policy.getScope()));
                else if (policy.getScope().getKind() == AuthorizationScopeKind.ORGANIZATION) throw new IllegalArgumentException("No ownership resolver for exact resource assignment");
            }
            for (var subject : subjects) { identifier(subject); result = result.compose(ignored -> validateIdentity(subject, policy.getScope())); }
            return result;
        } catch (Exception e) { return Future.failedFuture(e); }
    }

    private Future<Void> validateIdentity(String id, AuthorizationScope scope) {
        return identities.findById(id).compose(identity -> {
            boolean tenantMatches = scope.getKind() != AuthorizationScopeKind.APPLICATION_TENANT || Objects.equals(scope.getTenantId(), identity == null ? null : identity.getTenantId());
            if (identity == null || !identity.isEnabled() || identity.getType() == ParticipantIdentityType.DELEGATE
                    || !Objects.equals(scope.getOrganizationId(), identity.getOrganizationId())
                    || !Objects.equals(scope.getApplicationId(), identity.getApplicationId()) || !tenantMatches) return Future.failedFuture(new AuthorizationException("Identity is outside this access scope"));
            return Future.succeededFuture();
        });
    }
    private void unique(String id, Set<String> seen) { identifier(id); if (!seen.add(id)) throw new IllegalArgumentException("Duplicate id"); }
    private void identifier(String id) { if (id == null || id.isBlank() || id.equals("*") || id.length() > 512) throw new IllegalArgumentException("Invalid id"); }
    private void requireList(List<?> values, int maximum) { if (values == null || values.size() > maximum || values.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Invalid or oversized policy list"); }
}
