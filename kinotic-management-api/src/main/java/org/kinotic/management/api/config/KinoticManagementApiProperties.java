package org.kinotic.management.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link ManagementApiProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.managementApi.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticManagementApiProperties extends KinoticProperties {

    /**
     * Management-api properties configuration.
     */
    @Valid
    private ManagementApiProperties managementApi = new ManagementApiProperties();

    /**
     * If true, management-api functionality will not be loaded.
     */
    private boolean disableManagement = false;

}
