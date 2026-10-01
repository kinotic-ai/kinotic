package org.kinotic.management.internal.api.services;

import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;

/**
 * A project migration whose statements are already addressed at the indices of the entities they name.
 *
 * @param version the migration's version
 * @param name    the migration's name
 * @param content the addressed statements
 */
record ResolvedMigration(Integer version, String name, MigrationContent content) implements Migration {

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MigrationContent getContent() {
        return content;
    }
}
