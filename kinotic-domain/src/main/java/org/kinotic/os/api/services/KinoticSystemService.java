package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.KinoticSystem;
import org.kinotic.os.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface KinoticSystemService {

    CompletableFuture<KinoticSystem> getSystem();

    CompletableFuture<KinoticSystem> save(KinoticSystem system);

    /**
     * Returns the enabled OIDC configurations registered at the system scope.
     *
     * @return the enabled configurations, or an empty list if none are attached
     */
    CompletableFuture<List<OidcConfiguration>> getOidcConfigurations();

}

