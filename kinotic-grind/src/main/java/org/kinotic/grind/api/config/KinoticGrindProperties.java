package org.kinotic.grind.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes the grind module settings to the {@code kinotic} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticGrindProperties extends KinoticProperties {

    /**
     * When true the grind module is not loaded: no job execution, no run recording, and no
     * job monitoring beans are registered.
     */
    private boolean disableGrind = false;

}
