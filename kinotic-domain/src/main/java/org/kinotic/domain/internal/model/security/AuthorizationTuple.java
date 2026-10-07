package org.kinotic.domain.internal.model.security;

/** A persisted relationship projected from roles and assignments. */
public record AuthorizationTuple(String user, String relation, String object) {}
