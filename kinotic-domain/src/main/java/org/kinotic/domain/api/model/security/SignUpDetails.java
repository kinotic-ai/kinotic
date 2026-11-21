package org.kinotic.domain.api.model.security;

/**
 * What a person signing up to an application with an email and a password tells about themselves.
 *
 * @param displayName the name the person gave, at most 200 characters, or {@code null}
 * @param companyName the company the person signs up for, which becomes a tenant of an application whose tenants
 *                    its administrators manage; {@code null} where the system gives each user a tenant of its own
 */
public record SignUpDetails(String displayName, String companyName) {
}
