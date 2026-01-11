package org.mindignited.structures.sql;

import java.util.List;

import org.mindignited.structures.sql.domain.Migration;
import org.mindignited.structures.sql.domain.ResourceMigration;
import org.mindignited.structures.sql.executor.MigrationExecutor;
import org.mindignited.structures.sql.parsers.MigrationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads system migrations from the filesystem and applies them after the Elasticsearch client is configured
 * but before other components are initialized.
 */
@Component
public class SystemMigrator implements ApplicationListener<ContextRefreshedEvent> {
    private static final Logger log = LoggerFactory.getLogger(SystemMigrator.class);
    private static final String MIGRATIONS_PATH = "classpath:migrations/*.sql";
    
    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;
    private final ResourceLoader resourceLoader;
    
    public SystemMigrator(MigrationExecutor migrationExecutor, 
                         MigrationParser migrationParser,
                         ResourceLoader resourceLoader) {
        this.migrationExecutor = migrationExecutor;
        this.migrationParser = migrationParser;
        this.resourceLoader = resourceLoader;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Initializing system migrations...");
        try {
            PathMatchingResourcePatternResolver resolver = resourceLoader instanceof PathMatchingResourcePatternResolver ?
                (PathMatchingResourcePatternResolver) resourceLoader :
                new PathMatchingResourcePatternResolver(resourceLoader);

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