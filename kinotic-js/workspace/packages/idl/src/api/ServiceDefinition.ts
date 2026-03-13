import {AbstractDefinition} from '@/api/AbstractDefinition'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import { FunctionDefinition } from '@/api/FunctionDefinition'
import {HasQualifiedName} from '@/api/HasQualifiedName'

/**
 * Provides functionality to define an interface with a Continuum schema.
 * <p>
 * Created by navid on 2023-4-13.
 */
export class ServiceDefinition extends AbstractDefinition implements HasQualifiedName {

    /**
     * The namespace this {@link ServiceDefinition} belongs to
     */
    public namespace: string

    public constructor(name: string,
                       namespace: string,
                       decorators?: C3Decorator[],
                       metadata?: { [p: string]: any } | null) {
        super(name, decorators, metadata)
        this.namespace = namespace
    }

    /**
     * This defines {@link FunctionDefinition}'s for this {@link ServiceDefinition}
     * The key is the function name and the value is the schema that defines the function
     */
    public functions: Set<FunctionDefinition> = new Set<FunctionDefinition>()

    /**
     * Stores the given value in the functions definitions for this schema
     * If a schema for the name already exists an error will be thrown
     * @param func the function to add
     * @return this
     */
    public addFunction(func: FunctionDefinition): ServiceDefinition {
        if(this.functions.has(func)){
            throw new Error(`ServiceDefinition already contains function for name ${func.name}`)
        }
        this.functions.add(func)
        return this
    }

    /**
     * Gets the fully qualified name for this {@link ServiceDefinition} which is the namespace + "." + name
     * @return the fully qualified name for this {@link ServiceDefinition}
     */
    public getQualifiedName(): string {
        return this.namespace + "." + this.name
    }
}
