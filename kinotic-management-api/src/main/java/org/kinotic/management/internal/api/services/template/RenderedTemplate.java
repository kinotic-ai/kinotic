package org.kinotic.management.internal.api.services.template;

import java.util.Map;

/**
 * A project template rendered for one project: the files a new repository's baseline holds.
 *
 * @param renderedFiles the rendered text files, by their rendered path, every path contained in the repository
 * @param sources       the template path each rendered file was rendered from, by its rendered path
 * @param binaryFiles   the template's binary files, which are not rendered, by their path
 * @param templateFiles every file of the template as it was read, by its path
 */
public record RenderedTemplate(Map<String, String> renderedFiles,
                               Map<String, String> sources,
                               Map<String, TarballFile> binaryFiles,
                               Map<String, TarballFile> templateFiles) {

    /**
     * Whether the rendered file at {@code renderedPath} is executable: the template file it was rendered from is,
     * through a templated path (e.g. {@code bin/{{projectName}}.sh.liquid}) as well as an unchanged one.
     */
    public boolean isExecutable(String renderedPath) {
        String sourcePath = sources.get(renderedPath);
        TarballFile source = sourcePath != null ? templateFiles.get(sourcePath) : null;
        return source != null && source.executable();
    }
}
