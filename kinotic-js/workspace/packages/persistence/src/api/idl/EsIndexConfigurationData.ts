/**
 * Provides configuration for the Elasticsearch index.
 * This can be used to override the default index settings and mappings used by Structures.
 */
export class EsIndexConfigurationData {

    /**
     * The Elasticsearch component templates to use when creating this index.
     */
    public componentTemplates: string[] = []
}
