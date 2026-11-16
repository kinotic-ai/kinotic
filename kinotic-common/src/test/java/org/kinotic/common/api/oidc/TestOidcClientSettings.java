package org.kinotic.common.api.oidc;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** A registration at {@link FakeIdentityProvider}. */
@Getter
@Setter
@Accessors(chain = true)
class TestOidcClientSettings implements OidcClientSettings {

    private OidcProviderType provider;
    private String authority;
    private String clientId;
    private String audience;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String userEmailsUri;

}
