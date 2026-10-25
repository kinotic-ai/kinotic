package org.kinotic.domain.api.model.security;

import java.util.List;

/**
 * What an application's login, or one of its tenants', offers a person, for building its sign-in and sign-up
 * pages. Every login accepts an email and a password from an existing user.
 *
 * @param passwordSignUp whether a new person may sign up with an email and a password
 * @param providers      the sign-in providers, in the order to present them
 */
public record ApplicationLoginMethods(boolean passwordSignUp, List<ApplicationLoginProvider> providers) {
}
