package org.kinotic.migration;

import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.ResourceMigration;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads system migrations from the filesystem and applies them after the Elasticsearch client is configured
 * but before other components are initialized.
 */
public class SystemMigrator {
    private static final Logger log = LoggerFactory.getLogger(SystemMigrator.class);
    private static final String MIGRATIONS_PATH = "classpath:migrations/*.sql";

    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;
    private final Set<String> activeEnvironments;

    public SystemMigrator(MigrationExecutor migrationExecutor,
                          MigrationParser migrationParser,
                          Collection<String> activeEnvironments) {
        this.migrationExecutor = migrationExecutor;
        this.migrationParser = migrationParser;
        this.activeEnvironments = activeEnvironments.stream()
                                                    .filter(e -> e != null && !e.isBlank())
                                                    .map(e -> e.trim().toLowerCase(Locale.ROOT))
                                                    .collect(Collectors.toUnmodifiableSet());
    }

    public void execute() {
        log.info("Initializing system migrations...");
        try {
            migrationExecutor.ensureMigrationIndexExists().get();

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            Resource[] resources = resolver.getResources(MIGRATIONS_PATH);

            if (resources.length == 0) {
                log.info("No system migration files found");
                return;
            }

            log.info("Found {} system migration files", resources.length);

            // Select migrations applicable to the active environments; the executor's per-version check handles idempotency.
            List<Migration> migrationsToApply = new java.util.ArrayList<>();
            for (Resource resource : resources) {
                Migration migration = new ResourceMigration(resource, migrationParser);
                if (appliesToActiveEnvironments(migration)) {
                    migrationsToApply.add(migration);
                }
            }

            if (migrationsToApply.isEmpty()) {
                log.info("No system migrations apply to the active environments {}", activeEnvironments);
                return;
            }

            log.info("Applying {} candidate system migrations for active environments {}",
                     migrationsToApply.size(), activeEnvironments);

            migrationExecutor.executeSystemMigrations(migrationsToApply).get();
            log.info("System migrations processing complete");
        } catch (Exception e) {
            log.error("Error during system migration", e);
            throw new IllegalStateException("Failed to initialize system migrations", e);
        }
    }

    boolean appliesToActiveEnvironments(Migration migration) {
        Set<String> environments = migration.getEnvironments();
        if (environments.isEmpty()) {
            return true;
        }
        return environments.stream().anyMatch(activeEnvironments::contains);
    }

}