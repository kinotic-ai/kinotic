package org.kinotic.management.internal.api.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.services.EntityStatementResolver;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.model.MigrationDefinition;
import org.kinotic.management.api.model.MigrationRequest;
import org.kinotic.management.api.model.MigrationResult;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.services.MigrationService;
import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.ReindexStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs an organization's migrations against the entities of one of its applications. A migration names entities,
 * and every statement is addressed at the index of the entity it names before any of them runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMigrationService implements MigrationService {

    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;
    private final SecurityContext securityContext;
    private final ProjectRepository projectRepository;
    private final EntityStatementResolver entityStatementResolver;

    @Override
    public Future<MigrationResult> executeMigrations(MigrationRequest migrationRequest) {
        String projectId = migrationRequest.projectId();
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        log.debug("Executing {} migrations for project {}", migrationRequest.migrations().size(), projectId);
        return requireOwnedProject(projectId, participant)
                .compose(project -> resolve(migrationRequest.migrations(), project))
                .compose(migrations -> KinoticUtil.toFuture(migrationExecutor.executeProjectMigrations(migrations, projectId))
                                                          .map(v -> MigrationResult.success(projectId, migrations.size())))
                .otherwise(throwable -> {
                    log.debug("Failed to execute migrations for project {}: {}", projectId, throwable.getMessage(), throwable);
                    return MigrationResult.failure(projectId, throwable.getMessage());
                });
    }

    @Override
    public Future<Integer> getLastAppliedMigrationVersion(String projectId) {
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return requireOwnedProject(projectId, participant)
                .compose(project -> KinoticUtil.toFuture(migrationExecutor.getLastAppliedMigrationVersion(projectId)));
    }

    @Override
    public Future<Boolean> isMigrationApplied(String projectId, String version) {
        Validate.notNull(version, "Migration version cannot be null");
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return requireOwnedProject(projectId, participant)
                .compose(project -> KinoticUtil.toFuture(migrationExecutor.isMigrationAppliedAsync(version, projectId)));
    }

    /**
     * Loads a project of the participant's organization.
     */
    private Future<Project> requireOwnedProject(String projectId, OrganizationParticipant participant) {
        Validate.notNull(projectId, "Project ID cannot be null");
        // the system migrations are recorded under this project id, so a project of that id would read and
        // write their history
        Validate.isTrue(!MigrationExecutor.SYSTEM_PROJECT.equals(projectId), "Project ID %s is not allowed", projectId);
        String organizationId = participant.getOrganizationId();
        // a missing project and another organization's fail the same way: no existence oracle
        return projectRepository.findById(projectId, organizationId)
                                .map(project -> DomainUtil.requireOwned(project, organizationId, "Project " + projectId + " not found"));
    }

    /**
     * Parses every migration and addresses each of its statements at the index of the entity it names, so a
     * syntax error, a statement that is not a data statement, or a name that is not one of the application's
     * published entities fails the request before anything runs.
     */
    private Future<List<Migration>> resolve(List<MigrationDefinition> definitions, Project project) {
        ApplicationKey applicationKey = project.applicationKey();
        List<Future<Migration>> migrations = definitions.stream().map(definition -> resolve(definition, applicationKey)).toList();
        return Future.all(migrations).map(CompositeFuture::list);
    }

    private Future<Migration> resolve(MigrationDefinition definition, ApplicationKey applicationKey) {
        List<Statement> statements = migrationParser.parse(definition.content(), definition.name()).statements();
        for (Statement statement : statements) {
            Validate.isTrue(statement instanceof InsertStatement || statement instanceof UpdateStatement
                                    || statement instanceof DeleteStatement || statement instanceof ReindexStatement,
                            "Migration %s: a project migration acts on the application's entities with INSERT, UPDATE, DELETE and REINDEX, so %s is not allowed",
                            definition.name(), statement.getClass().getSimpleName().replace("Statement", ""));
        }
        return entityStatementResolver.resolve(statements, applicationKey)
                                      .map(resolved -> new ResolvedMigration(definition.version(), definition.name(), new MigrationContent(resolved)));
    }
}
