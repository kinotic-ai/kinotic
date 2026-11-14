package org.kinotic.authz.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The OpenFGA engine every authorization store lives in. Configured under {@code kinotic.authz.*}.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class AuthzProperties {

    /**
     * Base URL of OpenFGA's HTTP API; override per environment via {@code kinotic.authz.apiUrl}
     * (env {@code KINOTIC_AUTHZ_APIURL}).
     */
    @NotBlank
    private String apiUrl = "http://localhost:8080";

    /**
     * The pre-shared key OpenFGA authenticates API calls with, or empty when its API is unauthenticated, as in
     * local development.
     */
    private String apiToken = null;

    /**
     * Has the server place the records it finds stored in the authorization graph when it starts: the
     * organizations and applications not yet in it and, for a system with no administrator, its system users,
     * machines and nodes. For a deployment whose records are written by a migration or a script instead of the
     * services, which place what they create; set via {@code kinotic.authz.seedGraph}
     * (env {@code KINOTIC_AUTHZ_SEEDGRAPH}).
     */
    private boolean seedGraph = false;

}
