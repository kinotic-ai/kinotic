package org.mindignited.structures.migration;

import lombok.RequiredArgsConstructor;
import org.mindignited.structures.sql.SystemMigrator;
import org.mindignited.structures.sql.executor.MigrationExecutor;
import org.mindignited.structures.sql.parsers.MigrationParser;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the migration process when the application is ready.
 * Created By Navíd Mitchell 🤪on 1/31/26
 */
@Component
@RequiredArgsConstructor
public class MigrationInitializer {

    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        SystemMigrator systemMigrator = new SystemMigrator(migrationExecutor, migrationParser);
        systemMigrator.execute();

        System.exit(0); // Exit the application after migrations are complete
    }
}
