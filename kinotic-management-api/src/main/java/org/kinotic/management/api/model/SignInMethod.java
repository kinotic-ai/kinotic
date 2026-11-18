package org.kinotic.management.api.model;

import org.kinotic.common.api.oidc.OidcProviderType;
import org.kinotic.domain.api.model.security.AuthType;

import java.util.Date;

/**
 * One way the signed-in user authenticates, as the profile page lists them: the user's password, or a
 * provider identity bound to it. Nothing of the credential itself travels.
 *
 * @param id       what names the method to {@code ProfileService.removeSignInMethod}: the binding's id for a
 *                 provider identity, {@code "password"} for the password
 * @param type     password or provider identity
 * @param name     the name to show: the configuration the identity was asserted under, or "Password"
 * @param provider the provider type, for branding, or {@code null} for the password
 * @param created  when the method was added, or {@code null} for the password
 */
public record SignInMethod(String id, AuthType type, String name, OidcProviderType provider, Date created) {

    /** The id of the user's password as a sign-in method. */
    public static final String PASSWORD_ID = "password";
}
