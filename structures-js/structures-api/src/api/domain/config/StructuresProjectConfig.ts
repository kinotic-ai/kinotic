import {EntityConfiguration} from './EntityConfiguration.js'

/**
 * The project configuration for a structures project.
 * This is the base configuration. Language specific configurations extend this configuration.
 */
export abstract class StructuresProjectConfig {
    /**
     * This specifies the Model Definition Language (MDL).
     * This is the language that is used to define the structures models.
     * The following values are supported:
     * - ts: TypeScript
     * - ts-external: TypeScript with external models (defined by an external package)
     * - gql: GraphQL
     */
    public mdl!: 'ts' | 'ts-external' | 'gql'

    /**
     * The name of the project or undefined if a language specific project name is used.
     * i.e. if the project is typescript the package.json name will be used.
     */
    public name?: string

    /**
     * The description of the project.
     */
    public description?: string

    /**
     * The Structures Application that this project belongs to.
     */
    public application!: string

    /**
     * The paths to search for classes decorated with @Entity that Structures will be created for.
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

/**
 * The project configuration for a TypeScript structures project.
 */
export class TypescriptProjectConfig extends StructuresProjectConfig {

    public readonly mdl = 'ts'

}


/**
 * The project configuration for a TypeScript structures project.
 */
export class TypescriptExternalProjectConfig extends StructuresProjectConfig {

    public readonly mdl = 'ts-external'

    /**
     * External EntityConfigurations that are part of an external codebase and that you cannot add @Entity decorators to.
     */
    public externalEntities?: {
        [pathPattern: string] : EntityConfiguration[]
    }

    /**
     * The path to look for files that export functions that can be used by a {@link TransformConfiguration} or {@link CalculatedPropertyConfiguration}.
     */
    public utilFunctionsPaths?: string[]

}
