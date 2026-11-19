package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A provider the people who run this Kinotic deployment configured for the system console's login, which system
 * users sign in through. The system is its owner: its {@code organizationId} is the system's id,
 * {@code DomainUtil.SYSTEM_ID}, which no organization holds. It names one directory of its provider.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class SystemOidcConfiguration extends OwnedOidcConfiguration {

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.SYSTEM;
    }
}
