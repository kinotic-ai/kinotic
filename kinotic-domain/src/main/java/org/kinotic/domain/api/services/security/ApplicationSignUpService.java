package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginMethods;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;

import java.util.Map;

/**
 * How people become and sign in as users of an application at its login, or at one of its tenants': what the login
 * offers, the user a provider's verified identity signs in as, created on its first sign-in where the scope admits
 * new people, and sign-up with an email and a password. Every user created here has a valid tenant: the application's
 * login admits new people only while the system manages the application's tenants
 * ({@link TenantManagement#SYSTEM}), which gives each a tenant of its own, and a tenant's login admits them into
 * that tenant through the tenant's own providers only. Nothing here tells a caller whether an email has a user
 * before the person proves the email is theirs. Checks no participant: these serve the sign-in and sign-up flows,
 * which run before one is bound.
 */
public interface ApplicationSignUpService {

    /**
     * What the login of {@code scope} offers: its providers, each saying whether it signs new people up, and whether
     * new people may sign up with an email and a password.
     *
     * @param scope the login, of the application or of one of its tenants
     * @return the methods
     */
    Future<ApplicationLoginMethods> findLoginMethods(LoginScope scope);

    /**
     * The user of {@code scope} that the identity {@code configuration}'s provider verified signs in as: the user
     * bound to the identity, whichever registration with the provider verified it, so switching a configuration
     * between Kinotic's registration and the organization's own leaves its users as they were. An identity no user is
     * bound to becomes a new user, bound to it and created from its verified email and name, when the configuration
     * signs new people up and the scope admits them, as {@link #findLoginMethods} reports.
     *
     * @param scope          the login signed in to
     * @param configuration  the configuration the login offers that the person chose
     * @param verifiedClaims the claims the provider verified for the identity, carrying {@code sub}, {@code email}
     *                       and, where the provider gives it, a name
     * @return the user, or {@code null} when no user of the scope is bound to the identity and none is created; fails
     *         with {@link ExistingUserException} when a user would be created under an email the application already
     *         holds under another sign-in method, and with {@link UnverifiedEmailException} when the configuration
     *         admits every directory at the provider (Kinotic's registration, or Microsoft Entra's {@code common} or
     *         {@code organizations}) and the claims do not prove the email belongs to the person: a Microsoft Entra
     *         email needs {@code email_verified} or {@code xms_edov}, or a personal Microsoft account
     */
    Future<ApplicationParticipantIdentity> findOrCreateOidcUser(LoginScope scope,
                                                                OwnedOidcConfiguration configuration,
                                                                Map<String, Object> verifiedClaims);

    /**
     * Starts a sign-up with an email and a password: emails {@code email} a link to the {@code /signup/verify} page of
     * the UI at {@code uiOrigin}, carrying a single-use token valid for a day, which {@link #completePasswordSignUp}
     * takes with the password the person chooses. An email the application already has a user under is sent nothing,
     * as is one whose link from an earlier call is still valid, and the call completes as it does for any other, so
     * it tells nothing about the email.
     *
     * @param scope       the login, which must offer password sign-up ({@link ApplicationLoginMethods#passwordSignUp()},
     *                    so {@link Application#isPasswordSignUp()} at the application's own login); checked before the
     *                    call returns, with {@link IllegalStateException}
     * @param email       the email to verify, which must contain {@code @}, matched and stored trimmed and lowercased;
     *                    checked before the call returns, with {@link IllegalArgumentException}
     * @param uiOrigin    the origin of the application's UI the link opens, which the caller has checked is one of the
     *                    application's
     * @return completes once the email is sent, or once it is known none is
     */
    Future<Void> initiatePasswordSignUp(LoginScope scope, String email, String uiOrigin);

    /**
     * Completes a sign-up {@link #initiatePasswordSignUp} started: consumes the token and creates the application's
     * user with the verified email, {@code password} and {@code displayName}, with a tenant of its own. The name is
     * taken here, from the person who holds the email, so no earlier request for the address chooses it.
     *
     * @param scope    the login whose API host the completion was addressed to, which the sign-up must be to and must
     *                 still offer password sign-up; checked before the call returns, with {@link IllegalStateException}
     * @param token    the token the verification link carried
     * @param password the password the person chose
     * @param displayName the name the person gave, at most 200 characters, or {@code null}
     * @return the new user; fails with {@link IllegalArgumentException} when the token is unknown, spent, expired or
     *         for another application, or the name is longer than 200 characters, and {@link ExistingUserException}
     *         when the email has a user already
     */
    Future<ApplicationParticipantIdentity> completePasswordSignUp(LoginScope scope, String token, String password, String displayName);
}
