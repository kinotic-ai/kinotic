package org.kinotic.management.internal.api.services.template;

import com.github.slugify.Slugify;
import lombok.RequiredArgsConstructor;
import org.kinotic.management.api.model.Project;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Renders the project template a new project's repository starts from: the spawn-format contents (Liquid in paths
 * and {@code .liquid} files) rendered with the project's values.
 * <p>
 * Alongside the project's own values, the render context carries a version range for every published
 * {@code @kinotic-ai} npm package, keyed by the spawn global a template pins it through ({@code kinoticCoreVersion},
 * {@code kinoticCliVersion}, ...), so a provisioned project depends on the package versions this server ships with.
 * The project's name is also exposed to the templates as {@code projectSlug}, its slug, for values that must be
 * identifiers rather than the raw name.
 */
@Component
@RequiredArgsConstructor
public class ProjectTemplateRenderer {

    private static final Slugify SLUGIFY = Slugify.builder().build();
    /** The longest repository name GitHub and Gitea accept. */
    private static final int REPO_NAME_MAX = 100;
    private static final String NPM_PACKAGE_VERSIONS_RESOURCE = "/spawn/npm-package-versions.properties";

    private final GraalJsSpawnRenderer spawnRenderer;
    // Loaded on first render: the npmPackageVersions build task generates the resource, which a partial build
    // lacks, and such a build must still start a Spring context
    private volatile Map<String, Object> npmPackageVersions;

    /**
     * Renders the template a gzipped tarball holds, as a repository host's archive of a repository serves it,
     * for {@code project}. Blocking and CPU-bound: callers run it on a worker thread.
     *
     * @throws IllegalArgumentException when the tarball, or a rendered path, names a file outside the repository
     */
    public RenderedTemplate render(Project project, byte[] tarGz) {
        Map<String, TarballFile> entries = TemplateTarball.parse(tarGz);

        Map<String, String> textFiles = new LinkedHashMap<>();
        Map<String, TarballFile> binaryFiles = new LinkedHashMap<>();
        for (Map.Entry<String, TarballFile> entry : entries.entrySet()) {
            String text = decodeUtf8(entry.getValue().content());
            if (text != null) {
                textFiles.put(entry.getKey(), text);
            } else {
                // Binary files bypass the renderer: liquid path templating doesn't apply to them
                binaryFiles.put(entry.getKey(), entry.getValue());
            }
        }

        SpawnRenderResult rendered = spawnRenderer.render(textFiles, contextFor(project));
        // A property value can inject ../ into a path the source entry didn't contain, so it isn't covered by the
        // parse-time check on tarball entries
        rendered.files().keySet().forEach(RepoTreePath::requireContained);
        return new RenderedTemplate(rendered.files(), rendered.sources(), binaryFiles, entries);
    }

    /**
     * The name a project's repository is created with: the project name's slug, slugified as {@code DefaultProjectService}
     * derives the project id, so a name that passes its id-uniqueness check produces an identically-shaped
     * repository name, truncated to the length a repository host accepts.
     */
    public String repoName(Project project) {
        String ret = SLUGIFY.slugify(project.getName());
        if (ret.length() > REPO_NAME_MAX) {
            ret = ret.substring(0, REPO_NAME_MAX);
        }
        return ret;
    }

    private Map<String, Object> contextFor(Project project) {
        // The package versions seed the context, so a template that also declares them as
        // spawn.json globals gets this server's values instead of its own defaults.
        Map<String, Object> ret = new LinkedHashMap<>(npmPackageVersions());
        // projectName is the human-facing name; projectSlug is its slug, for template
        // values that must be npm identifiers (e.g. the package.json name), which the
        // raw name is not. It is not truncated to a repository host's name length limit:
        // an npm identifier shouldn't inherit it.
        ret.put("projectName", project.getName());
        ret.put("projectSlug", SLUGIFY.slugify(project.getName()));
        ret.put("organizationId", project.getOrganizationId());
        ret.put("applicationId", project.getApplicationId());
        ret.put("projectId", project.getId());
        return ret;
    }

    // Two first renders racing both load the same immutable map, so no lock is needed
    private Map<String, Object> npmPackageVersions() {
        Map<String, Object> ret = npmPackageVersions;
        if (ret == null) {
            ret = loadNpmPackageVersions();
            npmPackageVersions = ret;
        }
        return ret;
    }

    private static Map<String, Object> loadNpmPackageVersions() {
        Properties properties = new Properties();
        try (InputStream in = ProjectTemplateRenderer.class.getResourceAsStream(NPM_PACKAGE_VERSIONS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Spawn context resource " + NPM_PACKAGE_VERSIONS_RESOURCE + " is missing from the classpath. "
                        + "It is generated from the kinotic-js package.json files by the "
                        + "kinotic-management-api npmPackageVersions task.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + NPM_PACKAGE_VERSIONS_RESOURCE, e);
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        properties.forEach((name, range) -> ret.put((String) name, range));
        return Map.copyOf(ret);
    }

    /** @return the bytes decoded as strict UTF-8, or null when they are not valid UTF-8 (binary). */
    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
