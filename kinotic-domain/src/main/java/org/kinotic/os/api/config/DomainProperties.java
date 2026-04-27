package org.kinotic.os.api.config;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * Created By Navíd Mitchell 🤪on 4/26/26
 */
@Getter
@Setter
public class DomainProperties {

    /**
     * Email / outbound-mail configuration.
     */
    private EmailProperties email = new EmailProperties();

    /**
     * Platform-wide OIDC providers (Google, Microsoft, etc.) bootstrapped at startup.
     */
    private PlatformOidcProperties oidc = new PlatformOidcProperties();

}
