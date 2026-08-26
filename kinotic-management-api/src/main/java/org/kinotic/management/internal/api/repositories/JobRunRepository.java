package org.kinotic.management.internal.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobRunRepository extends AbstractRepository<JobRun> {

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_job_run", JobRun.class, crudServiceTemplate);
    }

    /**
     * Returns the page of runs recorded for the given job name.
     */
    public Future<Page<JobRun>> findByName(String name, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("name", name)));
    }

    /**
     * Returns the page of runs owned by the given {@link JobOwner}: all of an organization's
     * runs, narrowed to an application and/or project when the owner carries those ids. The
     * system owner selects platform runs - those owned by no organization.
     */
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
            owner.getOrganizationId() != null ? termFilter("organizationId", owner.getOrganizationId())
                                              : missingFilter("organizationId"),
            owner.getApplicationId() != null ? termFilter("applicationId", owner.getApplicationId()) : null,
            owner.getProjectId() != null ? termFilter("projectId", owner.getProjectId()) : null)));
    }

}
