package org.kinotic.migration;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies environment-based migration selection in {@link SystemMigrator}.
 */
class SystemMigratorTest {

    private static SystemMigrator migrator(String... activeProfiles) {
        return new SystemMigrator(null, null, List.of(activeProfiles));
    }

    private static Migration withEnvironments(Set<String> environments) {
        return new Migration() {
            @Override
            public Integer getVersion() {
                return 1;
            }

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public MigrationContent getContent() {
                return new MigrationContent(List.of());
            }

            @Override
            public Set<String> getEnvironments() {
                return environments;
            }
        };
    }

    @Test
    void commonMigrationAlwaysApplies() {
        assertTrue(migrator("production").appliesToActiveEnvironments(withEnvironments(Set.of())));
        assertTrue(migrator().appliesToActiveEnvironments(withEnvironments(Set.of())));
    }

    @Test
    void matchingProfileApplies() {
        assertTrue(migrator("test").appliesToActiveEnvironments(withEnvironments(Set.of("dev", "test"))));
    }

    @Test
    void nonMatchingProfileDoesNotApply() {
        assertFalse(migrator("production").appliesToActiveEnvironments(withEnvironments(Set.of("dev", "test"))));
    }

    @Test
    void environmentTaggedMigrationSkippedWhenNoActiveProfiles() {
        assertFalse(migrator().appliesToActiveEnvironments(withEnvironments(Set.of("dev"))));
    }

    @Test
    void profileMatchingIsCaseInsensitive() {
        assertTrue(migrator("TEST").appliesToActiveEnvironments(withEnvironments(Set.of("test"))));
    }
}
