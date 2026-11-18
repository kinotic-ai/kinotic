package org.kinotic.systemserver.internal.api.rest;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.StringUtils;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.OAuthExtensionGrant;
import org.kinotic.domain.api.rest.OAuthServerHandler;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** The system server's authorization server, issued under its API base URL. */
@Component
public class SystemOAuthServerHandler extends OAuthServerHandler {

    private final KinoticManagementApiProperties properties;

    public SystemOAuthServerHandler(KinoticManagementApiProperties properties,
                                    AuthEndpointSupport authEndpointSupport,
                                    OAuthAuthorizationService oauthAuthorizationService,
                                    RefreshTokenService refreshTokenService,
                                    List<OAuthExtensionGrant> extensionGrants) {
        super(authEndpointSupport, oauthAuthorizationService, refreshTokenService, extensionGrants);
        Validate.validState(StringUtils.isNotBlank(properties.getManagementApi().getSystemApiBaseUrl()),
                            "kinotic.managementApi.systemApiBaseUrl must be set on the system server");
        this.properties = properties;
    }

    @Override
    public String issuer(RoutingContext ctx) {
        return properties.getManagementApi().getSystemApiBaseUrl();
    }
}
