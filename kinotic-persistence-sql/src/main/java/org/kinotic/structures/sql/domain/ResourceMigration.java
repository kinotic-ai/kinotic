package org.kinotic.structures.sql.domain;

import org.kinotic.structures.sql.parsers.MigrationParser;
import org.springframework.core.io.Resource;

/**
 * Migration implementation backed by a Spring Resource.
 * <p>
 * Version is extracted from the filename. Migration content is parsed lazily.
 * Any IO errors are wrapped in unchecked exceptions.
 */
public class ResourceMigration implements Migration {
    private final Resource resource;
    private final MigrationParser parser;
    private final int version;
    private final String name;
    private MigrationContent content;

    public ResourceMigration(Resource resource, MigrationParser parser) {
        this.resource = resource;
        this.parser = parser;
        this.name = resource.getFilename();
        this.version = extractVersionFromFilename(name);
    }

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
        if (content == null) {
            try {
                content = parser.parse(resource);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse migration content for: " + name, e);
            }
        }
        return content;
    }

    private int extractVersionFromFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("Migration filename is null");
        int idx = filename.indexOf("__");
        if (idx > 1 && filename.startsWith("V")) {
            String num = filename.substring(1, idx);
            try {
                return Integer.parseInt(num);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid migration version in filename: " + filename, e);
            }
        }
        throw new IllegalArgumentException("Invalid migration filename format: " + filename);
    }
} 