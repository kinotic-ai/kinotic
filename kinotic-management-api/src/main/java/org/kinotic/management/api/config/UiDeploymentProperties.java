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

}
