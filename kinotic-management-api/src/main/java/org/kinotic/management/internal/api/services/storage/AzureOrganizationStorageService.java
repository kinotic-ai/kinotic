package org.kinotic.management.internal.api.services.storage;

import com.azure.storage.file.datalake.sas.PathSasPermission;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.services.storage.AzureStorageUrlIssuer;
import org.kinotic.management.api.services.storage.OrganizationStorageService;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Data Lake SDK backed {@link OrganizationStorageService}. Reaches the organization storage account
 * at its configured blob endpoint as the server's Azure identity, which signs each URL with a user
 * delegation key for the one file it names.
 */
@Component
public class AzureOrganizationStorageService implements OrganizationStorageService {

    /** The one container of the organization storage account. */
    private static final String CONTAINER = "organizations";

    private final AzureStorageUrlIssuer organizations;

    public AzureOrganizationStorageService(Vertx vertx, KinoticManagementApiProperties kinoticProperties) {
        this.organizations = new AzureStorageUrlIssuer(vertx, kinoticProperties.getManagementApi().getOrganizationsStorageEndpoint());
    }

    @Override
    public Future<String> issueSbomWriteUrl(String organizationId, String projectId, Duration ttl) {
        return organizations.issueFileUrl(CONTAINER, sbomFile(organizationId, projectId), ttl,
                                          new PathSasPermission().setCreatePermission(true).setWritePermission(true));
    }

    @Override
    public Future<String> issueSbomReadUrl(String organizationId, String projectId, Duration ttl) {
        return organizations.issueFileUrl(CONTAINER, sbomFile(organizationId, projectId), ttl,
                                          new PathSasPermission().setReadPermission(true));
    }

    /**
     * A project's one SBOM file: {@code <organizationId>/sboms/<projectId>.cdx.json}. Everything the
     * platform keeps for an organization sits under its directory, partitioned by use.
     */
    private static String sbomFile(String organizationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(projectId, "projectId cannot be blank");
        return organizationId + "/sboms/" + projectId + ".cdx.json";
    }

}
