package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.repositories.ReconcilableRepository;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.UiDeployment;
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
public class UiDeploymentRepository extends AbstractRepository<UiDeployment> implements ReconcilableRepository<UiDeployment> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.UI_DEPLOYMENT, "kinotic_ui_deployment");

    /** More UIs than one project publishes, so a project's deployments are read in one page. */
    private static final int PROJECT_PAGE_SIZE = 500;

    private final WatchedStateRepository watchedStateRepository;
    private final ReconcileStateRepository reconcileStateRepository;

    public UiDeploymentRepository(CrudServiceTemplate crudServiceTemplate,
                                  WatchedStateRepository watchedStateRepository,
                                  ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED.name(), UiDeployment.class, crudServiceTemplate);
        this.watchedStateRepository = watchedStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
    }

    @Override
    public WatchedType type() {
        return WATCHED.type();
    }

    // Stored by label alone; the organization is what the repository of the project deployment it
    // belongs to needs to find that
    @Override
    public String scopeOf(UiDeployment record) {
        return record.getOrganizationId();
    }

    @Override
    public Future<UiDeployment> find(String id, String scope) {
        return findById(id);
    }

    @Override
    public Future<Page<UiDeployment>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(document(id), dirtyAt);
    }

    @Override
    public Future<Page<UiDeployment>> findUnreconciled(Pageable pageable) {
        return reconcileStateRepository.findUnreconciled(indexName, type, pageable);
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
     * Writes what the UI's deployment should be and enters the change in the ledger; visible to
     * search on completion. Fails for a deployment that does not exist.
     *
     * @param id      the deployment, the site's label
     * @param desired what the deployment should be
     * @param source  what caused it, for the ledger
     */
    public Future<Void> updateDesired(String id, DeploymentState desired, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.updateDesired(document(id), desired, null, source).mapEmpty();
    }

    /**
     * Writes what the UI's deployment is and which generation of intent that answers, and enters
     * the change in the ledger; visible to search on completion.
     *
     * @param id       the deployment
     * @param observed what the deployment is
     * @param seen     the generation of intent the report answers
     * @param source   what caused it, for the ledger
     */
    public Future<Void> reportObserved(String id, DeploymentState observed, long seen, String source) {
        Validate.notBlank(id, "id cannot be blank");
        return reconcileStateRepository.reportObserved(document(id), observed, seen, source).mapEmpty();
    }

    /**
     * @see ReconcileStateRepository#requestDeletion(WatchedDocument, String)
     */
    public Future<Void> requestDeletion(String id, String source) {
        return reconcileStateRepository.requestDeletion(document(id), source).mapEmpty();
    }

    /**
     * Records why the site does not yet serve what it should, or clears it with null, leaving every
     * other field as it is.
     */
    public Future<Void> recordFailure(String id, String message) {
        Validate.notBlank(id, "id cannot be blank");
        Map<String, Object> fields = new HashMap<>();
        fields.put("failureMessage", message);
        fields.put("updated", new Date());
        return crudServiceTemplate.partialUpdateSync(indexName, id, fields, false);
    }

    private static WatchedDocument document(String id) {
        return WatchedDocument.of(WATCHED, id);
    }
}
