package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where the platform serves each published UI from, bound under
 * {@code kinotic.managementApi.uiDeployment.*}. Every site is a hostname label under
 * {@link #sitesDomain}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UiDeploymentProperties {

    /**
     * When true no site is provisioned for a published UI and
     * {@code MockUiDeploymentProvisioner} marks every deployment ready at once, so publishing
     * works in development and tests without Front Door.
     */
    private boolean disableProvisioner = false;

    /**
     * The domain every site is a label under, e.g. {@code apps.kinotic.ai}: a UI published as
     * {@code acme-shop-admin} is served at {@code acme-shop-admin.apps.kinotic.ai}.
     */
    private String sitesDomain;

    /**
     * Id of the Azure DNS zone that holds {@link #sitesDomain}, where each site's CNAME and
     * validation TXT records are written.
     */
    private String dnsZoneId;

    /**
     * Id of the Front Door Standard profile every site is served through.
     */
    private String frontDoorProfileId;

    /**
     * Host name of the profile's endpoint, the target of every site's CNAME.
     */
    private String frontDoorEndpointHostName;

}
