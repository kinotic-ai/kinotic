package org.kinotic.grind.api.model;

/**
 * The icon keys the platform's consoles know, for {@link Task#icon(String)},
 * {@link JobDefinition#icon(String)} and {@link org.kinotic.grind.api.annotations.Task#icon()}. A
 * key a console does not know shows its default icon.
 */
public final class TaskIcons {

    /** Choosing where the work runs. */
    public static final String TARGET = "target";

    /** Source checked out from a repository. */
    public static final String GIT = "git";

    /** Built artifacts. */
    public static final String PACKAGE = "package";

    /** Server workloads. */
    public static final String SERVER = "server";

    /** Sites published to the web. */
    public static final String GLOBE = "globe";

    /** A software bill of materials. */
    public static final String SBOM = "sbom";

    private TaskIcons() {
    }

}
