package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Authenticates the credentials a client outside a browser presents in its request headers: a Kinotic access
 * token ({@code Authorization: Bearer}), or a {@code clientId} and {@code clientSecret} — an account's email and
 * password, acting in the organization the {@code organizationId} header names or in the platform when it names
 * none, an application user's email and password, checked in the application the {@code organizationId} and
 * {@code applicationId} headers select, or a machine's id and secret, whose scope those headers must agree with
 * when supplied. Each server's {@link SecurityService} delegates here with the participants it admits.
 */
public interface CredentialAuthenticationService {

    /**
     * Authenticates the credentials in {@code authenticationInfo} to the identity they prove and the participant
     * it acts as, admitting it only when {@code admitted} accepts the pair.
     *
     * @param authenticationInfo the request headers, whose names are matched ignoring case
     * @param admitted           whether the server accepts the identity, and the participant it acts as, that the
     *                           credentials prove
     * @return a future emitting the {@link Participant}, failed with an {@link AuthenticationException} for
     *         missing or invalid credentials and for a participant {@code admitted} refuses
     */
    Future<Participant> authenticate(Map<String, String> authenticationInfo,
                                     BiPredicate<ParticipantIdentity, Participant> admitted);

}
