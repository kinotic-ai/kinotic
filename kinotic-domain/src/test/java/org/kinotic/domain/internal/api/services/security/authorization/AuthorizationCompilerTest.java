package org.kinotic.domain.internal.api.services.security.authorization;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.authorization.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationCompilerTest {
    @Test void wildcardsRespectSegments() {
        assertTrue(AuthorizationCompiler.matches("projects.*", "projects.repo.initialize"));
        assertFalse(AuthorizationCompiler.matches("projects.*", "projectsExtra.update"));
        assertFalse(AuthorizationCompiler.matches("projects.*", "projects"));
    }
    @Test void scopesAreInjectiveAndTenantSeparated() {
        assertNotEquals(AuthorizationScope.encode("a.b", "c"), AuthorizationScope.encode("a", "b.c"));
        assertNotEquals(AuthorizationScope.encode("", "x"), AuthorizationScope.encode(null, "x"));
        var scope = scope("tenant-a");
        var other = scope("tenant-b");
        assertNotEquals(AuthorizationCompiler.resource(scope, "invoice", "same-id", "invoices.read"), AuthorizationCompiler.resource(other, "invoice", "same-id", "invoices.read"));
        assertThrows(IllegalArgumentException.class, () -> scope.setKind(AuthorizationScopeKind.ORGANIZATION).key());
    }
    @Test void contextualParentsIncludeOnlyOwnTenantAndApplication() {
        var scope = scope("tenant-a");
        var parents = AuthorizationCompiler.parents(scope, "invoice", "one", "invoices.read");
        assertEquals(4, parents.size());
        assertTrue(parents.stream().anyMatch(tuple -> tuple.user().equals(AuthorizationCompiler.grant(scope, "invoice", "one", "invoices.read"))));
        assertFalse(parents.stream().anyMatch(tuple -> tuple.user().equals(AuthorizationCompiler.grant(scope("tenant-b"), "invoice", "one", "invoices.read"))));
    }
    @Test void removingOneOverlappingAssignmentPreservesTheEffectiveGrant() {
        var scope = scope("tenant-a");
        var policy = new AuthorizationPolicy().setScope(scope).setRoles(List.of(new AuthorizationRole().setId("editor").setPermissions(List.of("invoices.*"))));
        var first = assignment("first"); var second = assignment("second");
        var catalog = List.of(new AuthorizationPermission().setPermission("invoices.update").setResourceType("invoice"));
        policy.setAssignments(List.of(first, second));
        var before = AuthorizationCompiler.project(policy, catalog);
        policy.setAssignments(List.of(second));
        assertEquals(before, AuthorizationCompiler.project(policy, catalog));
        policy.setAssignments(List.of());
        assertTrue(AuthorizationCompiler.project(policy, catalog).isEmpty());
    }
    @Test void deniedGroupsRemainDistinctFromAllows() {
        var policy = new AuthorizationPolicy().setScope(scope("tenant-a"))
                .setGroups(List.of(new AuthorizationGroup().setId("contractors").setMemberIds(List.of("alice"))))
                .setRoles(List.of(new AuthorizationRole().setId("editor").setPermissions(List.of("invoices.update"))))
                .setAssignments(List.of(assignment("deny").setSubjectKind(AuthorizationSubjectKind.GROUP).setSubjectId("contractors").setEffect(AuthorizationEffect.DENY)));
        var tuples = AuthorizationCompiler.project(policy, List.of(new AuthorizationPermission().setPermission("invoices.update").setResourceType("invoice")));
        assertEquals(2, tuples.size());
        assertTrue(tuples.stream().anyMatch(tuple -> tuple.relation().equals("deny") && tuple.user().endsWith("#member")));
    }
    private AuthorizationAssignment assignment(String id) {
        return new AuthorizationAssignment().setId(id).setRoleId("editor").setSubjectKind(AuthorizationSubjectKind.IDENTITY).setSubjectId("alice")
                .setResourceType("invoice").setSelector(AuthorizationSelector.EXACT).setResourceId("one");
    }
    private AuthorizationScope scope(String tenant) {
        return new AuthorizationScope().setKind(AuthorizationScopeKind.APPLICATION_TENANT).setOrganizationId("acme").setApplicationId("billing").setTenantId(tenant);
    }
}
