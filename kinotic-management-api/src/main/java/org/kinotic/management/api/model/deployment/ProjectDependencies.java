package org.kinotic.management.api.model.deployment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * A project's SBOM: the dependency tree of the lockfile a deployment generated it from. Lists every
 * package version the lockfile installs, which of them the project's own package.json files
 * declare, which only development or optional dependencies reach, and which package depends on
 * which. The subsets and the edges name packages by their position in {@link #packages}.
 * {@link ProjectDeployment#isSbomGenerated()} says whether the tree lists the dependencies the
 * project's last sync reported. One row per project; {@link #id} equals the project id.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ProjectDependencies implements ApplicationScoped<String> {

    /**
     * The id of the project the tree belongs to.
     */
    private String id;

    private String organizationId;

    private String applicationId;

    /**
     * Every package version the lockfile installs, as a package URL, e.g.
     * {@code pkg:npm/%40isaacs/cliui@8.0.2}.
     */
    private List<String> packages = new ArrayList<>();

    /**
     * Positions in {@link #packages} of the packages the project's own package.json files declare;
     * every other package is transitive.
     */
    private List<Integer> direct = new ArrayList<>();

    /**
     * Positions in {@link #packages} of the packages that only development dependencies reach.
     */
    private List<Integer> development = new ArrayList<>();

    /**
     * Positions in {@link #packages} of the packages that only optional dependencies reach, such as
     * the builds of a native package for other platforms.
     */
    private List<Integer> optional = new ArrayList<>();

    /**
     * Which package depends on which: each edge is a pair of positions in {@link #packages}, the
     * dependent package first.
     */
    private List<int[]> edges = new ArrayList<>();

}
