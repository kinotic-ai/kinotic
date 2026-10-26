package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.SystemParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

/**
 * In-process service for verifying an email and password against the user of one scope. Not
 * {@code @Publish}-annotated: raw passwords never travel over RPC, only direct in-JVM calls (e.g. the
 * {@code POST /api/auth/org/login/:orgId} HTTP handler) or STOMP CONNECT credentials handled separately by the
 * {@code SecurityService}. Each method returns the user on success, or {@code null} for any failure (unknown
 * email, wrong password, a user with no password, disabled user); callers surface a generic message so the
 * client learns nothing about which case applies. A user of another scope with the same email never matches.
 */
public interface LocalAuthenticationService {

    /** Verifies {@code password} against the system user with {@code email}. */
    Future<SystemParticipantIdentity> authenticateSystemUser(String email, String password);

    /**
     * Verifies {@code password} against the user of organization {@code organizationId} with {@code email}, whose
     * login must take a password ({@link PasswordConfigurationService#findOffered}).
     */
    Future<OrganizationParticipantIdentity> authenticateOrganizationUser(String email, String password, String organizationId);

    /** Whether {@code password} is the password of {@code user}, an enabled user of any scope. */
    Future<Boolean> verifyPassword(UserParticipantIdentity user, String password);

    /**
     * Verifies {@code password} against the user of application {@code applicationId} of organization
     * {@code organizationId} with {@code email}, whose own login, its tenant's or else the application's, must take a
     * password ({@link PasswordConfigurationService#findOffered}).
     */
    Future<ApplicationParticipantIdentity> authenticateApplicationUser(String email, String password,
                                                                         String organizationId, String applicationId);
}
