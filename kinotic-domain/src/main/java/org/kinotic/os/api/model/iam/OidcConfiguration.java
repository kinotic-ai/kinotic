package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcConfiguration implements Identifiable<String> {

    private String id;

    private String name;

    private String provider;

    private boolean builtIn;

    private String clientId;

    private String authority;

    private String backchannelAuthority;

    private String redirectUri;

    private String postLogoutRedirectUri;

    private String silentRedirectUri;

    private List<String> domains;

    private String audience;

    private String rolesClaimPath;

    private String additionalScopes;

    private boolean enabled;

    private Date created;

    private Date updated;

}
