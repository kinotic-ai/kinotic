package org.kinotic.os.github.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes {@link GithubProperties} to the {@code kinotic} prefix. Mirrors
 * {@code KinoticDomainProperties} in shape so operators bind the GitHub App
 * settings under {@code kinotic.github.*} in helm values.
 * <p>
 * {@code @Validated} + {@code @Valid} cascade Bean Validation into
 * {@link GithubProperties}, so a misconfigured deployment fails at boot rather than
 * at first GitHub call.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticGithubProperties extends KinoticProperties {

    @Valid
    private GithubProperties github = new GithubProperties();

}
