package org.kinotic.authz.api.model;

/**
 * A role granted to a subject on a resource. The subject holds every permission the role bundles on the
 * resource and on everything inside it.
 *
 * @param id       the grant's id, which revokes it
 * @param roleId   the role granted, a built-in role's id or a custom role's
 * @param subject  who holds it
 * @param resource where it was made
 */
public record Grant(String id, String roleId, Subject subject, Resource resource) {
}
