package org.kinotic.management.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where the platform serves each published UI from, bound under
 * {@code kinotic.managementApi.uiDeployment.*}. Every site is a hostname label under
 * {@link #sitesDomain}. The Front Door and DNS fields are validated at boot, so an
 * environment that disables the provisioner still sets them, to placeholders.
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
    @NotBlank
    private String sitesDomain;

    /**
     * Id of the Azure DNS zone that holds {@link #sitesDomain}, where each site's CNAME and
     * validation TXT records are written.
     */
    @NotBlank
    private String dnsZoneId;

    /**
     * Id of the Front Door Standard profile every site is served through.
     */
    @NotBlank
    private String frontDoorProfileId;

    /**
     * Host name of the profile's endpoint, the target of every site's CNAME.
     */
    @NotBlank
    private String frontDoorEndpointHostName;

    /**
     * The hostname a site with the given label is served at.
     */
    public String resolveHostname(String label) {
        return label + "." + sitesDomain;
    }

    /**
     * Where a site with the given label is served.
     */
    public String resolveSiteUrl(String label) {
        return "https://" + resolveHostname(label);
    }

}
