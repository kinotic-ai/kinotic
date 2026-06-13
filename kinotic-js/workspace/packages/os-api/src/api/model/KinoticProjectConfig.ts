/**
 * Configuration for a single entities path and its corresponding repository output.
 */
export type EntitiesPathConfig = {
    /**
     * The path to search for classes decorated with @Entity.
     */
    path: string

    /**
     * The path where generated Repository classes will be placed.
     */
    repositoryPath: string

    /**
     * If true, the subfolder structure under the entities path will be mirrored under the repository path.
     * For example, if entitiesPath is "src/model" and contains "payments/Payment.ts",
     * the generated repository will be placed in "repositoryPath/payments/".
     * If false, all generated repositories are placed directly in the repositoryPath.
     * Defaults to true.
     */
    mirrorFolderStructure?: boolean
}

/**
 * The project configuration for a Kinotic project.
 */
export class KinoticProjectConfig {

    /**
     * The name of the project or undefined if a project name is used.
     * i.e. if the project is typescript the package.json name will be used.
     */
    public name?: string

    /**
     * The description of the project.
     */
    public description?: string

    /**
     * The Kinotic Organization that this project belongs to.
     */
    public organization!: string

    /**
     * The Kinotic Application that this project belongs to.
     */
    public application!: string

    /**
     * The paths to search for classes decorated with @Entity that Kinotic will be created for.
     * Each entry can be a string (simple path) or an {@link EntitiesPathConfig} object for full control
     * over where repository classes are generated.
     *
     * When a plain string is provided, the {@link generatedPath} will be used as the repository output path.
     */
    public entitiesPaths!: (string | EntitiesPathConfig)[]

    /**
     * The default path to where generated files will be placed when entitiesPaths contains plain strings.
     * Ignored for entitiesPaths entries that use {@link EntitiesPathConfig}.
     */
    public generatedPath?: string

    /**
     * The file extension to use for imports in generated files.
     */
    public fileExtensionForImports: string = '.js'

    /**
     * If true the generated Repository classes will validate all data before sending to the server.
     */
    public validate?: boolean

}
