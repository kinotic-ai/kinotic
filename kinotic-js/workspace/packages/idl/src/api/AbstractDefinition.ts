import {C3Decorator} from '@/api/decorators/C3Decorator'
import {HasDecorators} from '@/api/HasDecorators'
import {HasMetadata, MetadataType} from '@/api/HasMetadata'

/**
 * The base class for all C3 objects
 */
export abstract class AbstractDefinition implements HasDecorators, HasMetadata {

    /**
     * The name of this {@link AbstractDefinition}
     */
    public name: string

    /**
     * The list of Decorators that should be applied to this type
     */
    public decorators?: C3Decorator[] | null = null

    /**
     * The metadata keyword is legal on any {@link AbstractDefinition}, The objects provided must be serializable to JSON.
     * Usually, metadata is for putting things like descriptions or hints for code generators, or other things tools can use.
     */
    public metadata?: MetadataType | null = null

    protected constructor(name: string,
                          decorators?: C3Decorator[] | null,
                          metadata?: MetadataType | null) {
        this.name = name
        this.decorators = decorators
        this.metadata = metadata
    }

    /**
     * Checks if this type has a {@link C3Decorator} for the given type
     * @param value the {@link C3Decorator} to check for
     * @return true if the type has a {@link C3Decorator} for the given type false if not
     */
    public containsDecorator(value: C3Decorator): boolean {
        return this.findDecorator(value) !== null
    }

    /**
     * Checks if this type has any {@link C3Decorator}
     * @return true if any {@link C3Decorator}s are present false if not
     */
    public hasDecorators(): boolean {
        let ret = false
        if(this.decorators){
            ret = this.decorators.length > 0
        }
        return ret
    }

    /**
     * Adds a new decorator to this type
     * @param decorator the decorator to add
     * @return this {@link C3Base}
     */
    public addDecorator(decorator: C3Decorator): void {
        if(!this.decorators){
            this.decorators = []
        }else{
            if (this.containsDecorator(decorator)) {
                throw new Error(`C3Base already contains decorator for name ${decorator.type}`);
            }
        }
        this.decorators.push(decorator)
    }

    /**
     * Finds a decorator for the given type if it exists
     * @param value the type to find
     * @return the decorator if it exists null if not
     */
    public findDecorator(value: C3Decorator): C3Decorator | null {
        let ret: C3Decorator | null = null
        if (this.decorators) {
            for (const decorator of this.decorators) {
                if(decorator.type === value.type) {
                    ret = decorator
                    break
                }
            }
        }
        return ret
    }
}
