package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectRepository extends AbstractApplicationScopedRepository<Project> {

    public ProjectRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project", Project.class, crudServiceTemplate);
    }

    public Future<List<Project>> findByRepoFullName(String repoFullName) {
        return findAll(Pageable.ofSize(50),
                       b -> b.query(termFilter("repoFullName", repoFullName)))
                .map(Page::getContent);
    }

    public Future<List<Project>> findByRepoFullName(String repoFullName, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(Pageable.ofSize(50),
                       b -> b.routing(orgId).query(composeOrgFilter(orgId, termFilter("repoFullName", repoFullName))))
                .map(Page::getContent);
    }
}
