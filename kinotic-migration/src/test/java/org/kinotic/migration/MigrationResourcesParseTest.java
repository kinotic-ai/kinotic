package org.kinotic.migration;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every migration shipped on the classpath parses, so a syntax error fails the build
 * rather than the migration container at deploy time.
 */
class MigrationResourcesParseTest {

    @Test
    void everyShippedMigrationParses() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:migrations/*.sql");

        assertTrue(resources.length > 0, "no migrations found on the classpath");

        // Scanning the parsers package injects the same StatementParser set the application wires,
        // so a statement type added there is covered here without updating this test.
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext("org.kinotic.sql.parsers")) {

            MigrationParser parser = context.getBean(MigrationParser.class);
            for (Resource resource : resources) {
                parser.parse(resource);
            }
        }
    }
}
