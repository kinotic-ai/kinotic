package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.AuthenticationContext;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Authenticates the credentials a client outside a browser presents in its request headers: a Kinotic access
 * token ({@code Authorization: Bearer}), or a {@code clientId} and {@code clientSecret} — a user's email and
 * password, checked against the kind of user the scope headers select: an application's user in the application
 * the {@code organizationId} and {@code applicationId} headers name, an organization user acting in the
 * organization the {@code organizationId} header names, or a system user when they name none — or a machine's id
 * and secret, whose scope those headers must agree with
 * when supplied. Each server's {@link SecurityService} delegates here with the surface it serves and the
 * participants it admits.
 */
public interface CredentialAuthenticationService {

    /**
     * Authenticates the credentials in {@code authenticationInfo} to the identity they prove and the participant
     * it acts as, admitting it only when {@code admitted} accepts the pair. An access token proves its identity
     * only at the surface it was minted for: a token minted for any surface other than the one {@code context}
     * names is refused, whatever it carries. A client secret, a machine secret and a session are not minted for
     * a surface and ignore it.
     *
     * @param authenticationInfo the request headers, whose names are matched ignoring case
     * @param context            what the entry point asks for beside the credentials, the surface it serves among it
     * @param admitted           whether the server accepts the identity, and the participant it acts as, that the
     *                           credentials prove
     * @return a future emitting the {@link Participant}, failed with an {@link AuthenticationException} for
     *         missing or invalid credentials, a token of another surface, and a participant {@code admitted} refuses
     */
    Future<Participant> authenticate(Map<String, String> authenticationInfo,
                                     AuthenticationContext context,
                                     BiPredicate<ParticipantIdentity, Participant> admitted);

}
