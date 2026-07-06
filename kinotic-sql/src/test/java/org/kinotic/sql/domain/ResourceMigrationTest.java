package org.kinotic.sql.domain;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies version and environment extraction from migration filenames.
 */
class ResourceMigrationTest {

    private static Resource named(String filename) {
        return new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private static ResourceMigration migration(String filename) {
        return new ResourceMigration(named(filename), null);
    }

    @Test
    void commonMigrationHasNoEnvironments() {
        ResourceMigration migration = migration("V1__init.sql");
        assertEquals(1, migration.getVersion());
        assertTrue(migration.getEnvironments().isEmpty());
    }

    @Test
    void multipleEnvironmentsParsed() {
        ResourceMigration migration = migration("V3__kinotic_test_users.dev.test.sql");
        assertEquals(3, migration.getVersion());
        assertEquals(Set.of("dev", "test"), migration.getEnvironments());
    }

    @Test
    void environmentTokensNormalizedToLowercase() {
        assertEquals(Set.of("prod"), migration("V10__x.PROD.sql").getEnvironments());
    }

    @Test
    void underscoresInDescriptionAreNotEnvironments() {
        assertTrue(migration("V5__a_b_c.sql").getEnvironments().isEmpty());
    }

    @Test
    void blankEnvironmentTokensAreIgnored() {
        assertEquals(Set.of("dev"), migration("V3__x..dev.sql").getEnvironments());
    }

    @Test
    void versionUnaffectedByEnvironmentTokens() {
        assertEquals(3, migration("V3__x.dev.test.sql").getVersion());
    }

    @Test
    void missingSqlSuffixThrows() {
        assertThrows(IllegalArgumentException.class, () -> migration("V1__init.txt"));
    }
}
