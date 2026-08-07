package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class JobRunRepository extends AbstractRepository<JobRun> {

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_job_run", JobRun.class, crudServiceTemplate);
    }

    /**
     * Returns the page of runs recorded for the given job name.
     */
    public CompletableFuture<Page<JobRun>> findByName(String name, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("name", name)));
    }

    /**
     * Returns the page of runs owned by the given hierarchy: all of an organization's runs,
     * narrowed to an application and/or project when those ids are given.
     */
    public CompletableFuture<Page<JobRun>> findAllForOwner(String organizationId,
                                                           String applicationId,
                                                           String projectId,
                                                           Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
            termFilter("organizationId", organizationId),
            applicationId != null ? termFilter("applicationId", applicationId) : null,
            projectId != null ? termFilter("projectId", projectId) : null)));
    }

}
