package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.UiDeployment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores {@link UiDeployment}s, the standing deployments of a project's UI artifacts, keyed
 * by the site's hostname label and listed by project. A row is created whole when its label is
 * minted; every later write is a partial or scripted update, so the state the platform keeps on
 * a record is never written back from a read copy.
 */
@Component
public class UiDeploymentRepository extends AbstractReconcilableRepository<UiDeployment, DeploymentState> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.UI_DEPLOYMENT, "kinotic_ui_deployment");

    /** More UIs than one project publishes, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    public UiDeploymentRepository(CrudServiceTemplate crudServiceTemplate,
                                  WatchedStateRepository watchedStateRepository,
                                  WatchEventRepository watchEventRepository,
                                  ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED, UiDeployment.class, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    @Override
    public String scopeOf(UiDeployment record) {
        return record.getOrganizationId();
    }

    /**
     * Lists the deployments of the project's UIs, ordered by name.
     *
     * @param projectId the project whose UI deployments to list
     * @return a future emitting the deployments, empty when the project has none
     */
    public Future<List<UiDeployment>> findAllForProject(String projectId) {
        Validate.notBlank(projectId, "projectId cannot be blank");
        return findAll(Pageable.ofSize(PROJECT_PAGE_SIZE), b -> b.query(termFilter("projectId", projectId)))
                .map(page -> page.getContent().stream()
                                 .sorted(Comparator.comparing(UiDeployment::getName))
                                 .toList());
    }

    /**
     * Records what the site answered when last checked, or clears it with null, leaving every other
     * field as it is.
     */
    public Future<Void> recordObservation(String id, String observation) {
        Validate.notBlank(id, "id cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("observation", observation);
        fields.put("updated", new Date());
        return crudServiceTemplate.partialUpdateSync(indexName, id, fields, false);
    }

}
