package org.mindignited.structures.sql;

import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.mindignited.structures.sql.domain.Migration;
import org.mindignited.structures.sql.domain.ResourceMigration;
import org.mindignited.structures.sql.executor.MigrationExecutor;
import org.mindignited.structures.sql.parsers.MigrationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

/**
 * Loads system migrations from the filesystem and applies them after the Elasticsearch client is configured
 * but before other components are initialized.
 */
public class SystemMigrator implements Service {
    private static final Logger log = LoggerFactory.getLogger(SystemMigrator.class);
    private static final String MIGRATIONS_PATH = "classpath:migrations/*.sql";

    @SpringResource(resourceClass = MigrationExecutor.class)
    private transient MigrationExecutor migrationExecutor;
    @SpringResource(resourceClass = MigrationParser.class)
    private transient MigrationParser migrationParser;
//    @SpringResource(resourceClass = ResourceLoader.class)
//    private transient ResourceLoader resourceLoader;

    @Override
    public void execute() throws Exception {
        log.info("Initializing system migrations...");
        try {
            migrationExecutor.ensureMigrationIndexExists().get();

//            PathMatchingResourcePatternResolver resolver = resourceLoader != null
//                    && resourceLoader instanceof PathMatchingResourcePatternResolver ?
//                    (PathMatchingResourcePatternResolver) resourceLoader :
//                    new PathMatchingResourcePatternResolver(resourceLoader);

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

            Resource[] resources = resolver.getResources(MIGRATIONS_PATH);

            if (resources.length == 0) {
                log.info("No system migration files found");
                return;
            }

            log.info("Found {} system migration files", resources.length);

            // Get the last applied migration version for the system project
            Integer lastAppliedVersion = migrationExecutor.getLastAppliedMigrationVersion(MigrationExecutor.SYSTEM_PROJECT).get();

            // Load only migrations that need to be applied
            List<Migration> migrationsToApply = new java.util.ArrayList<>();
            for (Resource resource : resources) {
                Migration migration = new ResourceMigration(resource, migrationParser);
                if (lastAppliedVersion == null || migration.getVersion() > lastAppliedVersion) {
                    migrationsToApply.add(migration);
                }
            }

            if (migrationsToApply.isEmpty()) {
                log.info("All system migrations are already applied (last applied version: {})", lastAppliedVersion);
                return;
            }

            log.info("Applying {} new system migrations (starting from version {})",
                     migrationsToApply.size(),
                     lastAppliedVersion != null ? lastAppliedVersion + 1 : "1");

            migrationExecutor.executeSystemMigrations(migrationsToApply).get();
            log.info("System migrations processing complete");
        } catch (Exception e) {
            log.error("Error during system migration", e);
            throw new IllegalStateException("Failed to initialize system migrations", e);
        }
    }
    
}