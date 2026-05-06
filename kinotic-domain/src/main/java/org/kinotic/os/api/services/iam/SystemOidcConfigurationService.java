package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.iam.SystemOidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface SystemOidcConfigurationService extends IdentifiableCrudService<SystemOidcConfiguration, String> {

    /**
     * Returns every enabled system-admin OIDC config. Today this is a single row, but
     * the table allows growth if the platform later supports multiple admin IdPs.
     */
    CompletableFuture<List<SystemOidcConfiguration>> findAllEnabled();

}