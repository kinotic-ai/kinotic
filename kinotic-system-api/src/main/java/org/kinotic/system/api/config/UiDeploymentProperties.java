package org.kinotic.system.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where the platform stores each published UI, bound under {@code kinotic.systemApi.uiDeployment.*}. The domain its
 * sites are served under is {@code kinotic.domain.publishedSites.domain}. The storage endpoint is validated at boot,
 * so an environment whose site storage is {@link SiteStorageType#DISABLED} still sets it, to a placeholder.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UiDeploymentProperties {

    /**
     * The container of the sites storage account every site's directory is in.
     */
    public static final String SITES_CONTAINER = "sites";

    /**
     * Where published UIs' files are stored.
     */
    @NotNull
    private SiteStorageType siteStorage = SiteStorageType.AZURE;

    /**
     * Blob endpoint of the sites storage account every published UI is written to, under
     * {@code sites/<hostname>/}, from which the edge serves it. Workloads upload through it, so it is the
     * address a workload reaches the account at.
     */
    @NotBlank
    private String sitesStorageEndpoint;

    /**
     * Whether published UIs' files are stored, so a deployment uploads them and a removed UI's files are deleted.
     */
    public boolean storesSites() {
        return siteStorage != SiteStorageType.DISABLED;
    }

    /**
     * The directory of the site with the given hostname in the sites storage account, the URL a workload
     * appends each file's path to.
     */
    public String resolveSiteDirectoryUrl(String hostname) {
        String endpoint = sitesStorageEndpoint.endsWith("/")
                ? sitesStorageEndpoint.substring(0, sitesStorageEndpoint.length() - 1)
                : sitesStorageEndpoint;
        return endpoint + "/" + SITES_CONTAINER + "/" + hostname;
    }

}
