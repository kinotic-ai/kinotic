package org.kinotic.authz.api.model;

/**
 * One relationship in a store: {@code user} holds {@code relation} on {@code object}. The user and the object
 * are in OpenFGA's {@code type:id} form, and the user may be a userset, {@code type:id#relation}, or the
 * wildcard {@code type:*}.
 *
 * @param user     who holds the relation
 * @param relation the relation held
 * @param object   what it is held on
 */
public record RelationshipTuple(String user, String relation, String object) {
}
