package org.kinotic.sql.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the default contract of the {@link Migration} interface.
 */
class MigrationTest {

    @Test
    void getEnvironmentsDefaultsToEmpty() {
        Migration migration = new Migration() {
            @Override
            public Integer getVersion() {
                return 1;
            }

            @Override
            public String getName() {
                return "V1__init.sql";
            }

            @Override
            public MigrationContent getContent() {
                return new MigrationContent(java.util.List.of());
            }
        };
        assertTrue(migration.getEnvironments().isEmpty());
    }
}
