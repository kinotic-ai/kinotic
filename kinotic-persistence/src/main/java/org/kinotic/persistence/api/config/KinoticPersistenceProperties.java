package org.kinotic.persistence.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link PersistenceProperties} to the kinotic prefix
 * Created By Navíd Mitchell 🤪on 2/25/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticPersistenceProperties extends KinoticProperties {

    /**
     * If true, persistence functionality will not be loaded.
     */
    private boolean disablePersistence = false;

    /**
     * Persistence properties configuration
     * NOTE: will be ignored if {@link KinoticPersistenceProperties#disablePersistence} = true
     */
    @Valid
    private PersistenceProperties persistence = new PersistenceProperties();

}
