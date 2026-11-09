package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.AuthenticationContext;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A rejection of the credentials is an {@link AuthenticationException}; a failure of the stores behind the checks
 * fails the authentication as itself, so a caller can tell an outage from a wrong credential.
 *
 * Created by Navíd Mitchell 🤪 on 11/8/26.
 */
public class DefaultCredentialAuthenticationServiceTest {

    private static final AuthenticationContext CONTEXT = new AuthenticationContext(AuthenticationContext.PUBLISHED_SERVICES);

    private ParticipantIdentityService identityService;
    private KinoticJwtIssuer jwtIssuer;
    private DefaultCredentialAuthenticationService service;

    @BeforeEach
    public void setUp() {
        identityService = mock(ParticipantIdentityService.class);
        jwtIssuer = mock(KinoticJwtIssuer.class);
        service = new DefaultCredentialAuthenticationService(identityService, mock(LocalAuthenticationService.class), jwtIssuer);
    }

    @Test
    public void testMachineStoreFailureIsNotARejection() {
        IllegalStateException storeDown = new IllegalStateException("store down");
        when(identityService.verifyMachineCredentials("machine-1", "secret")).thenReturn(Future.failedFuture(storeDown));

        Future<Participant> authenticated = service.authenticate(Map.of("clientId", "machine-1", "clientSecret", "secret"), CONTEXT, (identity, participant) -> true);

        assertTrue(authenticated.failed());
        assertSame(storeDown, authenticated.cause());
    }

    @Test
    public void testMachineMissIsTheGenericRejection() {
        when(identityService.verifyMachineCredentials("machine-1", "wrong")).thenReturn(Future.failedFuture(new AuthenticationException("Invalid credentials")));

        Future<Participant> authenticated = service.authenticate(Map.of("clientId", "machine-1", "clientSecret", "wrong"), CONTEXT, (identity, participant) -> true);

        assertTrue(authenticated.failed());
        assertInstanceOf(AuthenticationException.class, authenticated.cause());
    }

    @Test
    public void testBlankMachineCredentialsAreRejectedBeforeAnyLookup() {
        Future<Participant> authenticated = service.authenticate(Map.of("clientId", "machine-1", "clientSecret", " "), CONTEXT, (identity, participant) -> true);

        assertTrue(authenticated.failed());
        assertInstanceOf(AuthenticationException.class, authenticated.cause());
        verify(identityService, never()).verifyMachineCredentials(anyString(), anyString());
    }

    @Test
    public void testIdentityStoreFailureOnAJwtIsNotARejection() {
        IllegalStateException storeDown = new IllegalStateException("store down");
        when(jwtIssuer.authenticate("token", CONTEXT)).thenReturn(Future.succeededFuture(User.create(new JsonObject().put("sub", "user-1"))));
        when(identityService.findById("user-1")).thenReturn(Future.failedFuture(storeDown));

        Future<Participant> authenticated = service.authenticate(Map.of("Authorization", "Bearer token"), CONTEXT, (identity, participant) -> true);

        assertTrue(authenticated.failed());
        assertSame(storeDown, authenticated.cause());
    }

    @Test
    public void testUnknownSubjectOfAJwtIsRejected() {
        when(jwtIssuer.authenticate("token", CONTEXT)).thenReturn(Future.succeededFuture(User.create(new JsonObject().put("sub", "user-1"))));
        when(identityService.findById("user-1")).thenReturn(Future.succeededFuture(null));

        Future<Participant> authenticated = service.authenticate(Map.of("Authorization", "Bearer token"), CONTEXT, (identity, participant) -> true);

        assertTrue(authenticated.failed());
        assertInstanceOf(AuthenticationException.class, authenticated.cause());
        verify(identityService, never()).resolveParticipant(any(ParticipantIdentity.class), any(), any());
    }
}
