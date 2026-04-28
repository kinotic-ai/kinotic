package org.kinotic.os.github.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes {@link GithubProperties} to the {@code kinotic} prefix. Mirrors
 * {@code KinoticDomainProperties} in shape so operators bind the GitHub App
 * settings under {@code kinotic.github.*} in helm values.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticGithubProperties extends KinoticProperties {

    private GithubProperties github = new GithubProperties();

}
