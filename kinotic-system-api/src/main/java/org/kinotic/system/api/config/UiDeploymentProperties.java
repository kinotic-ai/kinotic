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
 * at boot, so an environment without Azure storage still sets them, to placeholders.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UiDeploymentProperties {

    /**
     * The domain every organization's sites are under, e.g. {@code kinotic.io}: the UI {@code admin} of
     * application {@code shop} of organization {@code acme} is served at {@code shop--admin.acme.kinotic.io}.
     */
    @NotBlank
    private String sitesDomain;

    /**
     * Blob endpoint of the sites storage account every published UI is written to, under
     * {@code sites/<hostname>/}, from which the edge serves it.
     */
    @NotBlank
    private String sitesStorageEndpoint;

    /**
     * The hostname a site with the given id is served at.
     */
    public String resolveHostname(String siteId) {
        return siteId + "." + sitesDomain;
    }

    /**
     * Where a site with the given id is served.
     */
    public String resolveSiteUrl(String siteId) {
        return "https://" + resolveHostname(siteId);
    }

}
