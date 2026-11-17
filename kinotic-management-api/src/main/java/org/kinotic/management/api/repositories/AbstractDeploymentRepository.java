package org.kinotic.management.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OrganizationScoped;
import org.kinotic.domain.api.model.Reconcilable;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.DeploymentState;
import org.kinotic.management.api.model.deployment.DeploymentStatusType;

/**
 * {@link AbstractReconcilableRepository} for the standing deployments of a project's artifacts, each scoped to the
 * organization the project belongs to: adds the reads that find an organization's deployments and the suspended ones.
 *
 * @param <T> the kind of deployment
 */
public abstract class AbstractDeploymentRepository<T extends Reconcilable<DeploymentState> & OrganizationScoped<String>>
        extends AbstractReconcilableRepository<T, DeploymentState> {

    protected AbstractDeploymentRepository(WatchedIndex watched,
                                           Class<T> type,
                                           CrudServiceTemplate crudServiceTemplate,
                                           WatchedStateRepository watchedStateRepository,
                                           WatchEventRepository watchEventRepository,
                                           ReconcileStateRepository reconcileStateRepository) {
        super(watched, type, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    @Override
    public String scopeOf(T record) {
        return record.getOrganizationId();
    }

    /**
     * Lists the deployments of every project of the organization, in the order the pageable sorts them.
     */
    public Future<Page<T>> findAllForOrganization(String organizationId, Pageable pageable) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return findAll(pageable, b -> b.query(termFilter("organizationId", organizationId)));
    }

    /**
     * Lists the deployments, of every organization, last observed suspended, in the order the pageable sorts them.
     */
    public Future<Page<T>> findSuspended(Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("state.observed.phase", DeploymentStatusType.SUSPENDED.name())));
    }
}
