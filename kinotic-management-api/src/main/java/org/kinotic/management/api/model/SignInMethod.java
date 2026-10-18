package org.kinotic.management.api.model;

import org.kinotic.domain.api.model.security.OidcProviderKind;

import java.util.Date;

/**
 * One way the signed-in account authenticates, as the profile page lists them: the account's password, or a
 * provider identity bound to it. Nothing of the credential itself travels.
 *
 * @param id       what names the method to {@code ProfileService.removeSignInMethod}: the binding's id for a
 *                 provider identity, {@code "password"} for the password
 * @param kind     password or provider identity
 * @param name     the name to show: the configuration the identity was asserted under, or "Password"
 * @param provider the provider kind, for branding, or {@code null} for the password
 * @param created  when the method was added, or {@code null} for the password
 */
public record SignInMethod(String id, SignInMethodKind kind, String name, OidcProviderKind provider, Date created) {

    /** The id of the account's password as a sign-in method. */
    public static final String PASSWORD_ID = "password";
}
