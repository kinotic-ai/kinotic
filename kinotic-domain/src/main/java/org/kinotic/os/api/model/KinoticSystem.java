package org.kinotic.os.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

/**
 * Singleton entity representing the Kinotic OS deployment itself.
 * Holds the system-level authentication configuration, including which
 * OIDC providers are available for system administrator login.
 * <p>
 * The ID is always fixed to "kinotic-system". Managed via KinoticSystemService.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class KinoticSystem implements Identifiable<String> {

    private String id;

    /**
     * OIDC configuration IDs available for system-level authentication.
     * References {@link org.kinotic.os.api.model.iam.OidcConfiguration} entities by ID.
     */
    private List<String> oidcConfigurationIds;

    private Date updated;

}
