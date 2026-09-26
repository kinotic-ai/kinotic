package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Authenticates the credentials a client outside a browser presents in its request headers: a Kinotic access
 * token ({@code Authorization: Bearer}), or a {@code clientId} and {@code clientSecret} — a user's email and
 * password, checked in the scope the {@code organizationId} and {@code applicationId} headers select, or a
 * machine's id and secret, whose scope those headers must agree with when supplied. Each server's
 * {@link SecurityService} delegates here with the identities it admits.
 */
public interface CredentialAuthenticationService {

    /**
     * Authenticates the credentials in {@code authenticationInfo} to the identity they prove, admitting it only
     * when {@code admitted} accepts it.
     *
     * @param authenticationInfo the request headers, whose names are matched ignoring case
     * @param admitted           whether the server accepts the identity the credentials prove
     * @return a future emitting the {@link Participant} of the identity, failed with an
     *         {@link AuthenticationException} for missing or invalid credentials and for an identity
     *         {@code admitted} refuses
     */
    Future<Participant> authenticate(Map<String, String> authenticationInfo, Predicate<ParticipantIdentity> admitted);

}
