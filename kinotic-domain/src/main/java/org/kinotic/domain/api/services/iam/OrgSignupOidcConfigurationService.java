package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OrgSignupOidcConfigurationService extends IdentifiableCrudService<OrgSignupOidcConfiguration, String> {

    /**
     * Returns every enabled signup config — the source of the social button list rendered
     * on signup and login pages.
     */
    CompletableFuture<List<OrgSignupOidcConfiguration>> findAllEnabled();

    /**
     * Returns the single enabled config for the given provider kind, or {@code null} if
     * none is configured. Used by the social-button start endpoints to resolve the
     * correct config from the {@code :provider} path param.
     */
    CompletableFuture<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider);

}