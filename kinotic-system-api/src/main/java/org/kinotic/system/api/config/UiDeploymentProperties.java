package org.kinotic.system.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where the platform serves each published UI from, bound under
 * {@code kinotic.systemApi.uiDeployment.*}. Every site is a hostname label under
 * {@link #sitesDomain}, and its files live in the sites storage account. Both are validated
 * at boot, so an environment that disables the provisioner still sets them, to placeholders.
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
     * Blob endpoint of the sites storage account every published UI is written to, under
     * {@code sites/<hostname>/}, from which Front Door serves it.
     */
    @NotBlank
    private String sitesStorageEndpoint;

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
