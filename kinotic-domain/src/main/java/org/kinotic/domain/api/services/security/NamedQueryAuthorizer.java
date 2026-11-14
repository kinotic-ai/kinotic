package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.sql.domain.Statement;

/**
 * Checks a caller for the permission a named query's statement needs on the rows of its entity definition:
 * {@code can_edit} for an INSERT and an UPDATE, {@code can_delete} for a DELETE and {@code can_read} for any other.
 * The check is the one a request to the entities repositories gets, made for the statement in hand.
 */
public interface NamedQueryAuthorizer {

    /**
     * Checks the caller for the permission the statement needs.
     *
     * @param participant        the caller
     * @param entityDefinitionId the entity definition the statement acts on
     * @param statement          the statement about to run
     * @param acrossTenants      true for a statement acting on another tenant's rows than the caller's own, which is
     *                           checked on the definition itself; false for one checked within the caller's tenant
     * @return a future completing when the caller holds the permission, failing with an
     *         {@link org.kinotic.core.api.exceptions.AuthorizationException} when it does not
     */
    Future<Void> authorize(ScopedParticipant participant, String entityDefinitionId, Statement statement, boolean acrossTenants);

}
