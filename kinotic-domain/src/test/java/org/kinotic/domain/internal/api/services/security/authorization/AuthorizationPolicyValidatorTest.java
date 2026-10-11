package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationPolicyValidatorTest {
    @Test void bootstrapAcceptsImmutableAdministratorLists() throws Exception {
        var identities = mock(ParticipantIdentityRepository.class);
        var identity = new UserParticipantIdentity(); identity.setId("alice"); identity.setOrganizationId("acme"); identity.setEnabled(true);
        when(identities.findById("alice")).thenReturn(Future.succeededFuture(identity));
        var validator = new AuthorizationPolicyValidator(identities, List.of());
        validator.validate(policy(), List.of()).toCompletionStage().toCompletableFuture().get(1, TimeUnit.SECONDS);
    }
    @Test void administratorCannotBelongToAnotherOrganization() {
        var identities = mock(ParticipantIdentityRepository.class);
        var identity = new UserParticipantIdentity(); identity.setId("alice"); identity.setOrganizationId("other"); identity.setEnabled(true);
        when(identities.findById("alice")).thenReturn(Future.succeededFuture(identity));
        var validator = new AuthorizationPolicyValidator(identities, List.of());
        assertThrows(Exception.class, () -> validator.validate(policy(), List.of()).toCompletionStage().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }
    @Test void unknownOrNonDelegableCapabilityIsRejected() {
        var validator = new AuthorizationPolicyValidator(mock(ParticipantIdentityRepository.class), List.of());
        var policy = policy().setRoles(List.of(new AuthorizationRole().setId("exporter").setPermissions(List.of("invoices.export"))));
        assertTrue(validator.validate(policy, List.of(new AuthorizationPermission().setPermission("invoices.read").setResourceType("invoice"))).failed());
    }
    private AuthorizationPolicy policy() {
        return new AuthorizationPolicy().setScope(new AuthorizationScope().setKind(AuthorizationScopeKind.ORGANIZATION).setOrganizationId("acme"))
                .setAdministrators(List.of("alice")).setRoles(List.of()).setGroups(List.of()).setAssignments(List.of());
    }
}
