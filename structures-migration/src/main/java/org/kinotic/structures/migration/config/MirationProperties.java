package org.kinotic.structures.migration.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Properties to configure migration module.
 * Created By Navíd Mitchell 🤪on 1/31/26
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "structures-migration")
@Validated
public class MirationProperties {

    @NotBlank
    private String elasticHost;

    @NotNull
    private Integer elasticPort;

    @NotBlank
    private String elasticScheme;

    private String elasticUsername = null;

    private String elasticPassword = null;

    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

}
