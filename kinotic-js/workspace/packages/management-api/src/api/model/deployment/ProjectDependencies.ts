/**
 * A project's SBOM: the dependency tree of the lockfile a deployment generated it from. Lists every
 * package version the lockfile installs, which of them the project's own package.json files
 * declare, which only development or optional dependencies reach, and which package depends on
 * which. The subsets and the edges name packages by their position in packages.
 * ProjectDeployment.sbomGenerated says whether the tree lists the dependencies the project's last
 * sync reported. One row per project; the id equals the project id.
 */
export class ProjectDependencies {

    /**
     * The id of the project the tree belongs to.
     */
    public id: string | null = null

    public organizationId!: string

    public applicationId!: string

    /**
     * Every package version the lockfile installs, as a package URL, e.g.
     * pkg:npm/%40isaacs/cliui@8.0.2.
     */
    public packages: string[] = []

    /**
     * Positions in packages of the packages the project's own package.json files declare; every
     * other package is transitive.
     */
    public direct: number[] = []

    /**
     * Positions in packages of the packages that only development dependencies reach.
     */
    public development: number[] = []

    /**
     * Positions in packages of the packages that only optional dependencies reach, such as the
     * builds of a native package for other platforms.
     */
    public optional: number[] = []

    /**
     * Which package depends on which: each edge is a pair of positions in packages, the dependent
     * package first.
     */
    public edges: [number, number][] = []

}
