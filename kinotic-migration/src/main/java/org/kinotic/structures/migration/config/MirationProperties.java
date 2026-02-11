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

    /**
     * The host of the elasticsearch instance to connect to and migrate data.
     */
    @NotBlank
    private String elasticHost;

    /**
     * The port of the elasticsearch instance to connect to and migrate data.
     */
    @NotNull
    private Integer elasticPort;

    /**
     * The scheme (http or https) of the elasticsearch instance to connect to and migrate data.
     */
    @NotBlank
    private String elasticScheme;

    /**
     * The username to use when connecting to the elasticsearch instance to migrate data. Optional, only needed if the elasticsearch instance requires authentication.
     */
    private String elasticUsername = null;

    /**
     * The password to use when connecting to the elasticsearch instance to migrate data. Optional, only needed if the elasticsearch instance requires authentication.
     */
    private String elasticPassword = null;

    /**
     * Helper method to determine if both elastic username and password are provided and non-blank.
     * @return true if both elasticUsername and elasticPassword are non-null and not blank, false otherwise.
     */
    public boolean hasElasticUsernameAndPassword(){
        return elasticUsername != null && !elasticUsername.isBlank() && elasticPassword != null && !elasticPassword.isBlank();
    }

}
