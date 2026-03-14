/**
 * The configuration for a function that will transform a value before it is sent to the server.
 * The function must be exported from a file that is specified in {@link TypescriptExternalProjectConfig.utilFunctionsPaths}
 * The function return argument will be the resulting {@link C3Type} that will be configured for the Entity
 */
export class TransformConfiguration {
    /**
     * The json path to the property that should be transformed.
     * There must only be one {@link TransformConfiguration} per jsonPath.
     */
    jsonPath!: string

    /**
     * The name of the function that will be used to transform the value.
     */
    transformerFunctionName!: string
}
