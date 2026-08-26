package org.kinotic.management.api.config.github;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes {@link GithubProperties} to the {@code kinotic} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticManagementApiProperties extends KinoticProperties {

    @Valid
    private ManagementApiProperties managementApi = new ManagementApiProperties();

    private boolean disableManagement = false;

}
