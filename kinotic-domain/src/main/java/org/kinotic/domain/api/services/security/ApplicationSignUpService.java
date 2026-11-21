package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.exceptions.ExistingTenantException;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.UnverifiedEmailException;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.SignUpDetails;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;

import java.util.List;
import java.util.Map;

/**
 * How people become and sign in as users of an application at its login, or at one of its tenants': what the login
 * offers, the user a provider's verified identity signs in as, created on its first sign-in where the scope admits
 * new people, and sign-up with an email and a password. Every user created here has a valid tenant: at the
 * application's login the system gives a new person a tenant of its own where it manages the application's tenants
 * ({@link TenantManagement#SYSTEM}), and where the application's administrators manage them
 * ({@link TenantManagement#TENANT_ADMINISTRATORS}) the person names their company, which becomes a tenant they
 * administer, holding its {@code tenant.admin} role; a tenant's login admits new people into that tenant through
 * the tenant's own providers only. Nothing here tells a caller whether an email has a user
 * before the person proves the email is theirs. Checks no participant: these serve the sign-in and sign-up flows,
 * which run before one is bound.
 */
public interface ApplicationSignUpService {

    /**
     * The sign-in providers the login of {@code scope} offers, in the order to present them: its email and password,
     * when it takes one, then its OIDC providers, each saying whether it signs new people up.
     *
     * @param scope the login, of the application or of one of its tenants
     * @return the providers
     */
    Future<List<ApplicationLoginProvider>> findLoginProviders(LoginScope scope);

    /** Whether the login of {@code scope} signs new people up with an email and a password, as {@link #findLoginProviders} reports. */
    Future<Boolean> offersPasswordSignUp(LoginScope scope);

    /**
     * The user of {@code scope} that the identity {@code configuration}'s provider verified signs in as: the user
     * bound to the identity, whichever registration with the provider verified it, so switching a configuration
     * between Kinotic's registration and the organization's own leaves its users as they were. An identity no user is
     * bound to claims the user an administrator added to the scope under its email that nobody signs in as yet,
     * where the claims prove the email as sign-up requires, and is bound to it, spending the invitation the user was
     * sent; otherwise it becomes a new user, bound
     * to it and created from its verified email and name, when the configuration signs new people up and the scope
     * admits them, as {@link #findLoginProviders} reports. At the login of an application whose administrators
     * manage its tenants the new user founds the tenant of {@code companyName}, and a sign-in naming no company
     * creates no one.
     *
     * @param scope          the login signed in to
     * @param configuration  the configuration the login offers that the person chose
     * @param verifiedClaims the claims the provider verified for the identity, carrying {@code sub}, {@code email}
     *                       and, where the provider gives it, a name
     * @param companyName    the company a person signing up names, or {@code null}
     * @return the user, or {@code null} when no user of the scope is bound to the identity and none is created; fails
     *         with {@link ExistingUserException} when a user would be created under an email the application already
     *         holds under another sign-in method, with {@link ExistingTenantException} when the application already
     *         has a tenant for the company, and with {@link UnverifiedEmailException} when the configuration
     *         admits every directory at the provider (Kinotic's registration, or Microsoft Entra's {@code common} or
     *         {@code organizations}) and the claims do not prove the email belongs to the person: a Microsoft Entra
     *         email needs {@code email_verified} or {@code xms_edov}, or a personal Microsoft account; fails with
     *         {@link UserDisabledException} when the user the identity claims is disabled
     */
    Future<ApplicationParticipantIdentity> findOrCreateOidcUser(LoginScope scope,
                                                                OwnedOidcConfiguration configuration,
                                                                Map<String, Object> verifiedClaims,
                                                                String companyName);

    /**
     * Starts a sign-up with an email and a password: emails {@code email} a link to the {@code /signup/verify} page of
     * the UI at {@code uiOrigin}, carrying a single-use token valid for a day, which {@link #completePasswordSignUp}
     * takes with the password the person chooses. An email the application already has a user under is sent nothing,
     * as is one whose link from an earlier call is still valid, and the call completes as it does for any other, so
     * it tells nothing about the email.
     * <p>
     * When {@code kinotic.domain.email.enabled} is {@code false}, no email is sent and the link is returned for the
     * caller to show, so the address is never proven to be the caller's: a link from an earlier call that is still
     * valid is returned again, and an email the application already has a user under fails with
     * {@link ExistingUserException}.
     *
     * @param scope       the login, which must offer password sign-up ({@link #offersPasswordSignUp}: the application's
     *                    own login, whose {@link PasswordConfiguration} signs new people up); fails with
     *                    {@link IllegalStateException} otherwise
     * @param email       the email to verify, which must contain {@code @}, matched and stored trimmed and lowercased;
     *                    checked before the call returns, with {@link IllegalArgumentException}
     * @param uiOrigin    the origin of the application's UI the link opens, which the caller has checked is one of the
     *                    application's
     * @return a future emitting the verification link when email is disabled, or {@code null} once the email is sent
     *         or once it is known none is
     */
    Future<String> initiatePasswordSignUp(LoginScope scope, String email, String uiOrigin);

    /**
     * Completes a sign-up {@link #initiatePasswordSignUp} started: consumes the token and creates the application's
     * user with the verified email, {@code password} and the name given, with a tenant of its own: the one the
     * system gives it, or, where the application's administrators manage its tenants, the one created for the
     * company named, which the user administers. The details are taken here, from the person who holds the email,
     * so no earlier request for the address chooses them.
     *
     * @param scope    the login whose API host the completion was addressed to, which the sign-up must be to and must
     *                 still offer password sign-up; fails with {@link IllegalStateException} otherwise
     * @param token    the token the verification link carried
     * @param password the password the person chose
     * @param details  the name the person gave and the company they sign up for
     * @return the new user; fails with {@link IllegalArgumentException} when the token is unknown, spent, expired or
     *         for another application, the name is longer than 200 characters, or the application's administrators
     *         manage its tenants and no company is named, with {@link ExistingTenantException} when the application
     *         already has a tenant for the company, and with {@link ExistingUserException} when the email has a
     *         user already
     */
    Future<ApplicationParticipantIdentity> completePasswordSignUp(LoginScope scope, String token, String password, SignUpDetails details);
}
