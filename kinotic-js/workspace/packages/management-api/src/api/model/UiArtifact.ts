/**
 * One UI artifact of a project: a package directly under packages/ui of the project's
 * checkout whose package.json declares a build script.
 */
export interface UiArtifact {
    /**
     * The artifact's identity, the unscoped part of the name in the package's package.json:
     * "@acme/admin" is "admin". Always a single zone label.
     */
    name: string
    /**
     * The package directory, relative to the checkout root.
     */
    dir: string
}
