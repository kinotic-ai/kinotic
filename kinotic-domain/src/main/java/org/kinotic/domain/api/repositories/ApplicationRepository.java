package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class ApplicationRepository extends AbstractOrganizationScopedRepository<Application> {

    private static final String CLAIM_PRIMARY_UI_SCRIPT = """
            def app = ctx._source;
            if (app.primaryUiId != null) {
                ctx.op = 'noop';
            } else {
                app.primaryUiId = params.primaryUiId;
                app.primaryUiUrl = params.primaryUiUrl;
                app.updated = params.updated;
            }
            """;

    private static final String UPDATE_PRIMARY_UI_URL_SCRIPT = """
            def app = ctx._source;
            if (app.primaryUiId != params.primaryUiId || app.primaryUiUrl == params.primaryUiUrl) {
                ctx.op = 'noop';
            } else {
                app.primaryUiUrl = params.primaryUiUrl;
                app.updated = params.updated;
            }
            """;

    public ApplicationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_application", Application.class, crudServiceTemplate);
    }

    /**
     * Loads the application, failing when it does not exist in the given organization. The
     * guard for write paths that persist a reference to an application — reads are protected
     * by scope-composed queries instead, which simply miss for a foreign application.
     *
     * @param applicationId the application that must exist
     * @param organizationId the organization it must belong to
     * @return a future emitting the application, failed when it is not in the organization
     */
    public Future<Application> requireById(String applicationId, String organizationId) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findById(applicationId, organizationId)
                .map(app -> {
                    if (app == null) {
                        throw new IllegalArgumentException("Application not found: " + applicationId);
                    }
                    return app;
                });
    }

    /**
     * Designates the UI as the application's primary UI when the application has none, leaving a
     * designated primary UI as it is.
     *
     * @param applicationKey the application whose primary UI to designate
     * @param uiName         the name of the published UI
     * @param uiUrl          where the UI's site is served
     * @return a future emitting true when the UI became the primary UI, false when one was already designated
     */
    public Future<Boolean> claimPrimaryUi(ApplicationKey applicationKey, String uiName, String uiUrl) {
        Validate.notBlank(uiName, "uiName cannot be blank");
        Validate.notBlank(uiUrl, "uiUrl cannot be blank");
        return crudServiceTemplate.scriptedUpdate(indexName,
                                                  composeDocumentId(applicationKey.applicationId(), applicationKey.organizationId()),
                                                  CLAIM_PRIMARY_UI_SCRIPT,
                                                  Map.of("primaryUiId", uiName, "primaryUiUrl", uiUrl, "updated", new Date()),
                                                  b -> b.routing(applicationKey.organizationId()));
    }

    /**
     * Sets where the application's primary UI is served when the UI is its primary UI, leaving the application as
     * it is otherwise.
     *
     * @param applicationKey the application whose primary UI's address to set
     * @param uiName         the name of the published UI
     * @param uiUrl          where the UI's site is served
     * @return a future emitting true when the address changed, false when the UI is not the primary UI or the
     *         address was already {@code uiUrl}
     */
    public Future<Boolean> updatePrimaryUiUrl(ApplicationKey applicationKey, String uiName, String uiUrl) {
        Validate.notBlank(uiName, "uiName cannot be blank");
        Validate.notBlank(uiUrl, "uiUrl cannot be blank");
        return crudServiceTemplate.scriptedUpdate(indexName,
                                                  composeDocumentId(applicationKey.applicationId(), applicationKey.organizationId()),
                                                  UPDATE_PRIMARY_UI_URL_SCRIPT,
                                                  Map.of("primaryUiId", uiName, "primaryUiUrl", uiUrl, "updated", new Date()),
                                                  b -> b.routing(applicationKey.organizationId()));
    }
}
