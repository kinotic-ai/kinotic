package org.kinotic.sql.domain;

import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
    private final Set<String> environments;
    private MigrationContent content;

    public ResourceMigration(Resource resource, MigrationParser parser) {
        this.resource = resource;
        this.parser = parser;
        this.name = resource.getFilename();
        this.version = extractVersionFromFilename(name);
        this.environments = extractEnvironmentsFromFilename(name);
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
    public Set<String> getEnvironments() {
        return environments;
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

    private Set<String> extractEnvironmentsFromFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("Migration filename is null");
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".sql")) {
            throw new IllegalArgumentException("Invalid migration filename format: " + filename);
        }
        int idx = filename.indexOf("__");
        // The portion after the description segment ("V<n>__<description>") holds the optional, dot-delimited environment tokens.
        String afterDescription = filename.substring(idx + 2, filename.length() - ".sql".length());
        String[] tokens = afterDescription.split("\\.");
        Set<String> result = new LinkedHashSet<>();
        // The first token is the description; everything after it is an environment. Blank tokens (e.g. a double dot) are ignored.
        for (int i = 1; i < tokens.length; i++) {
            String env = tokens[i].trim().toLowerCase(Locale.ROOT);
            if (!env.isEmpty()) {
                result.add(env);
            }
        }
        return Collections.unmodifiableSet(result);
    }
} 