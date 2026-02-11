package org.kinotic.structures.migration;

import lombok.RequiredArgsConstructor;
import org.kinotic.structures.sql.SystemMigrator;
import org.kinotic.structures.sql.executor.MigrationExecutor;
import org.kinotic.structures.sql.parsers.MigrationParser;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Starts the migration process when the application is ready.
 * Created By Navíd Mitchell 🤪on 1/31/26
 */
@Component
@RequiredArgsConstructor
public class MigrationInitializer {

    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;
    private final ApplicationContext applicationContext;

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) throws IOException {
        SystemMigrator systemMigrator = new SystemMigrator(migrationExecutor, migrationParser);
        systemMigrator.execute();

        ((ConfigurableApplicationContext) applicationContext).close();
    }
}
