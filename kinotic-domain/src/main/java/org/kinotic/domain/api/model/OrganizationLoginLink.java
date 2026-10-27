package org.kinotic.domain.api.model;

/**
 * An organization a person is a member of, with the link to its login.
 *
 * @param name     the organization's display name
 * @param loginUrl the portal's login page of the organization
 */
public record OrganizationLoginLink(String name, String loginUrl) {
}
