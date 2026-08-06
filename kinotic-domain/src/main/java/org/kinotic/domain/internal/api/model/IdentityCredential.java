package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.domain.api.model.security.ParticipantIdentity;

/**
 * Internal entity storing the authentication secret of a {@link ParticipantIdentity} — a
 * LOCAL user's password or a MACHINE's client secret. Stored separately from
 * {@link ParticipantIdentity} so that secret hashes are never exposed through identity
 * CRUD operations.
 * <p>
 * The {@code id} matches the corresponding ParticipantIdentity's ID.
 * This entity is internal-only — it is not published via any service interface.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IdentityCredential implements Identifiable<String> {

    /**
     * Same as the corresponding ParticipantIdentity's ID.
     */
    private String id;

    /**
     * bcrypt hash of the identity's secret — a user's password or a machine's client secret.
     */
    private String secretHash;

}
