package org.kinotic.system.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Where the platform keeps the files it holds on behalf of organizations, bound under
 * {@code kinotic.systemApi.organizationStorage.*}: one storage account for every organization,
 * partitioned by organization and then by use. Validated at boot, so an environment without the
 * account still sets it, to a placeholder.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrganizationStorageProperties {

    /**
     * Blob endpoint of the organization storage account, e.g.
     * {@code https://stkinoticorgs.blob.core.windows.net/}.
     */
    @NotBlank
    private String blobEndpoint;

}
