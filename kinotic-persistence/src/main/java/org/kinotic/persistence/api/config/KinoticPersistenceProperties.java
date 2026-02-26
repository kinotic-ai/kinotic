package org.kinotic.persistence.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes the {@link PersistenceProperties} to the kinotic prefix
 * Created By Navíd Mitchell 🤪on 2/25/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticPersistenceProperties extends KinoticProperties {

    /**
     * Persistence properties configuration
     */
    private PersistenceProperties persistence = new PersistenceProperties();

}
