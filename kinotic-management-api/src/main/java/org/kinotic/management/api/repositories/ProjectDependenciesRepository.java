package org.kinotic.management.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stores each project's {@link ProjectDependencies}, one document per project keyed by the project
 * id.
 */
@Component
public class ProjectDependenciesRepository extends AbstractApplicationScopedRepository<ProjectDependencies> {

    private static final String PACKAGES_FIELD = "packages";

    // The fields a scan of a tree reads
    private static final List<String> SCANNED_FIELDS = List.of("id", "organizationId", "applicationId", PACKAGES_FIELD);

    public ProjectDependenciesRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project_dependencies", ProjectDependencies.class, crudServiceTemplate);
    }

    /**
     * Lists the trees of every organization, each carrying its id, organization, application and
     * package URLs; its positions in the package list are empty.
     *
     * @param pageable the page to return; sorted by {@code organizationId} then {@code id}, since an id
     *                 names one project only within its organization
     */
    public Future<Page<ProjectDependencies>> findAllOfEveryOrganization(CursorPageable pageable) {
        return findAll(pageable, b -> b.source(s -> s.filter(f -> f.includes(SCANNED_FIELDS))));
    }

    /**
     * Finds the trees of the projects, of any organization, that have one, each carrying its id,
     * organization, application and package URLs; its positions in the package list are empty.
     *
     * @param projectIdsByOrganization the ids of the projects to look for, by organization id
     * @return a future emitting the trees found, in no particular order
     */
    public Future<List<ProjectDependencies>> findAllExisting(Map<String, ? extends Collection<String>> projectIdsByOrganization) {
        Validate.notNull(projectIdsByOrganization, "projectIdsByOrganization cannot be null");
        List<MultiGetOperation> operations = new ArrayList<>();
        projectIdsByOrganization.forEach((orgId, ids) -> ids.forEach(id -> operations.add(MultiGetOperation.of(op -> op.index(indexName)
                                                                                                                        .id(composeDocumentId(id, orgId))
                                                                                                                        .routing(orgId)))));
        Future<List<ProjectDependencies>> ret;
        if (operations.isEmpty()) {
            ret = Future.succeededFuture(List.of());
        } else {
            ret = crudServiceTemplate.multiGet(operations, type, b -> b.sourceIncludes(SCANNED_FIELDS), null);
        }
        return ret;
    }

    /**
     * Lists the trees of every organization that list any version of any of the packages, each
     * carrying its id, organization, application and package URLs; its positions in the package
     * list are empty.
     *
     * @param purls    package URLs without a version, e.g. {@code pkg:npm/%40vitest/mocker}
     * @param pageable the page to return; sorted by {@code organizationId} then {@code id}
     */
    public Future<Page<ProjectDependencies>> findAllHoldingAnyVersionOf(Collection<String> purls, CursorPageable pageable) {
        Validate.notEmpty(purls, "purls cannot be empty");
        // the one literal @ of a package URL starts its version, so the prefix matches only that package
        List<Query> versionsOf = purls.stream()
                                      .map(purl -> Query.of(q -> q.prefix(p -> p.field(PACKAGES_FIELD).value(purl + "@"))))
                                      .toList();
        return findAll(pageable, b -> b.query(q -> q.bool(bool -> bool.should(versionsOf).minimumShouldMatch("1")))
                                       .source(s -> s.filter(f -> f.includes(SCANNED_FIELDS))));
    }

}
