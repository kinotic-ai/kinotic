package org.kinotic.os.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes the {@link EmailProperties} to the {@code kinotic} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticDomainProperties extends KinoticProperties {

    /**
     * Email / outbound-mail configuration.
     */
    private EmailProperties email = new EmailProperties();

}
