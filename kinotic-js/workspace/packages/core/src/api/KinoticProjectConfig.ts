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
     * The Kinotic Application that this project belongs to.
     */
    public application!: string

    /**
     * The paths to search for classes decorated with @Entity that Kinotic will be created for.
     */
    public entitiesPaths!: string[]

    /**
     * The path to where generated files will be placed.
     */
    public generatedPath!: string

    /**
     * The file extension to use for imports in generated files.
     */
    public fileExtensionForImports: string = '.js'

    /**
     * If true the generated EntityService classes will validate all data before sending to the server.
     */
    public validate?: boolean

}

