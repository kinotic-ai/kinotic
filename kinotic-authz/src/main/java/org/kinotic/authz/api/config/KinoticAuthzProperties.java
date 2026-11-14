package org.kinotic.authz.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link AuthzProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.authz.*}
 *  * Created by Navíd Mitchell 🤪on 10/4/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticAuthzProperties extends KinoticProperties {

    /**
     * Authorization engine configuration.
     */
    @Valid
    private AuthzProperties authz = new AuthzProperties();

}
