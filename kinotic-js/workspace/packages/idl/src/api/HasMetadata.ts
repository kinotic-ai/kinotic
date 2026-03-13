export type MetadataType = {[key: string]: any}

/**
 * Interface for objects that have metadata.
 */
export interface HasMetadata {

    metadata?: MetadataType | null

}
