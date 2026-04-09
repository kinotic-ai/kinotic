package org.kinotic.os.internal.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

/**
 * Internal entity storing password hashes for local (email/password) authentication.
 * Stored separately from {@link org.kinotic.os.api.model.iam.IamUser} so that password
 * hashes are never exposed through user CRUD operations.
 * <p>
 * The {@code id} matches the corresponding IamUser's ID.
 * This entity is internal-only — it is not published via any service interface.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IamCredential implements Identifiable<String> {

    /**
     * Same as the corresponding IamUser's ID.
     */
    private String id;

    /**
     * bcrypt hash of the user's password.
     */
    private String passwordHash;

}
