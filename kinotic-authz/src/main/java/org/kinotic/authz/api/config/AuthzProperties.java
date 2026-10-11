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

}
