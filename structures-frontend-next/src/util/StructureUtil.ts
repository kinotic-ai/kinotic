import {C3Decorator, PropertyDefinition} from '@kinotic/continuum-idl'

export class StructureUtil {
    public static hasDecorator(decoratorName: string, decorators: C3Decorator[] | undefined): boolean {
        let hasDecorator: boolean = false
        if(decorators !== undefined && decorators.length > 0){
            decorators.forEach((decorator) => {
                if(decorator.type === decoratorName){
                    hasDecorator = true
                }
            })
        }
        return hasDecorator
    }

    public static getPropertyDefinition(propertyName: string, propertyDefinitions: PropertyDefinition[]): PropertyDefinition | null {
        let definition = null
        for(const property of propertyDefinitions){
            if(property.name === propertyName){
                definition = property
                break
            }
        }
        return definition
    }
}
