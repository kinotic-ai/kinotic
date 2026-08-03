package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;

/**
 * Internal entity storing password hashes for local (email/password) authentication.
 * Stored separately from {@link ParticipantIdentity} so that password
 * hashes are never exposed through user CRUD operations.
 * <p>
 * The {@code id} matches the corresponding ParticipantIdentity's ID.
 * This entity is internal-only — it is not published via any service interface.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IamCredential implements Identifiable<String> {

    /**
     * Same as the corresponding ParticipantIdentity's ID.
     */
    private String id;

    /**
     * bcrypt hash of the user's password.
     */
    private String passwordHash;

}
