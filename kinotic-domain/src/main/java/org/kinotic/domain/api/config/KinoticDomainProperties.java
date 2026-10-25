package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import jakarta.validation.Valid;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes domain-level configuration (e.g. {@link EmailProperties}) under the
 * {@code kinotic.domain} prefix, and the system secret files under {@code kinotic.systemSecrets}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticDomainProperties extends KinoticProperties {

    @Valid
    private DomainProperties domain = new DomainProperties();

    /**
     * Paths to system-level secret files (the JWT signing keys).
     * Files are mounted into the pod by the Azure Key Vault CSI driver in production or
     * by a Kubernetes Secret volume locally, and watched for changes so rotation flows
     * through without a restart.
     */
    private SystemSecretsProperties systemSecrets = new SystemSecretsProperties();

}
