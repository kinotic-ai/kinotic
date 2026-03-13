/**
 * Interface for objects that have a qualified name
 */
export interface HasQualifiedName {

    /**
     * This is the namespace of this such as "org.kinotic.continuum.idl.api.schema"
     */
    namespace: string

    /**
     * This is the name of this such as "Person", "Animal"
     */
    name: string

    /**
     * The fully qualified name of this such as "org.kinotic.continuum.idl.api.schema.Person"
     * @return the fully qualified name of this
     */
    getQualifiedName(): string

}
