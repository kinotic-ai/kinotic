package org.kinotic.persistence.internal.endpoints.openapi;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 3/18/23.
 */
public interface OpenApiService {

    /**
     * Gets the OpenAPI spec for a given application
     * @param applicationId the application to get the OpenAPI spec for
     * @return the OpenAPI spec
     */
    CompletableFuture<OpenAPI> getOpenApiSpec(String applicationId);

}
