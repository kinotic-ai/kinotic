import {Type, Symbol, DecoratableNode} from 'ts-morph'
import {C3Type, ObjectC3Type, PropertyDefinition} from '@kinotic/continuum-idl'
import {ConverterConstants} from '../ConverterConstants.js'
import {TypescriptConversionState} from './TypescriptConversionState.js'
import {IConversionContext} from '../IConversionContext.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {tsDecoratorToC3Decorator, convertPrecisionToC3Type} from './ConverterUtils.js'
import {TenantIdDecorator} from '@kinotic/structures-api'

/**
 * Converts a typescript Class, Interface, or Type to a C3Type
 */
export class ObjectLikeToC3Type implements ITypeConverter<Type, C3Type, TypescriptConversionState>{

    convert(value: Type, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): C3Type {
        let ret: ObjectC3Type

        const name = value.getSymbolOrThrow("No Symbol could be found for object: "+value.getText()).getName()
        const namespace = conversionContext.state().application
        ret = new ObjectC3Type(name, namespace)

        // Object stack name is used to help name properties that are not defined such as with union literals
        conversionContext.state().objectNameStack.push(name)

        // We store the original source path so, it can be used later
        const declarations = value.getSymbol()?.getDeclarations()
        if(conversionContext.state().shouldAddSourcePathToMetadata
            && declarations
            && declarations.length > 0){
            if(!ret.metadata){
                ret.metadata = {}
            }
            ret.metadata[ConverterConstants.SOURCE_FILE_PATH] = declarations[0].getSourceFile().getFilePath()
        }

        // Get value declaration so we can get the decorators
        this.convertDecorators(value, ret)

        // Translate all the typescript properties
        this.convertProperties(value, conversionContext, ret)

        // Now add any calculated properties, we do this, so we can override any of the default properties
        this.convertCalculatedProperties(conversionContext, ret)

        conversionContext.state().objectNameStack.pop()

        return ret
    }

    supports(value: Type): boolean {
        return value.isObject() && !value.isArray();
    }

    private convertCalculatedProperties(conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>, ret: ObjectC3Type) {
        const calculatedProperties = conversionContext.state().getCalculatedProperties(conversionContext.currentJsonPath)
        if (calculatedProperties) {
            for (const calcProp of calculatedProperties) {
                const functionName = calcProp.calculatedPropertyFunctionName
                const calcFunction = conversionContext.state().getUtilFunctionByName(functionName)
                if (!calcFunction) {
                    throw new Error(`Could not find util function: ${functionName} for calculated property ${calcProp.entityJsonPath}.${calcProp.propertyName}`)
                }
                const converted = conversionContext.convert(calcFunction.getReturnType())
                ret.properties.push(new PropertyDefinition(calcProp.propertyName, converted, calcProp.decorators))
            }
        }
    }

    private convertDecorators(value: Type, ret: ObjectC3Type) {
        const errText = "No value declaration could be found for object " + value.getText()
        const valueDeclaration = value.getSymbolOrThrow(errText)
                                                   .getValueDeclarationOrThrow(errText)

        // Typescript cannot detect that this can be a DecoratableNode, so we have to cast it twice
        const decoratableNode = valueDeclaration as unknown as DecoratableNode

        // Now Add any class level decorators
        const decorators = decoratableNode.getDecorators()
        if (decorators) {
            for (const decorator of decorators) {
                const c3Decorator = tsDecoratorToC3Decorator(decorator)
                if (c3Decorator) {
                    ret.addDecorator(c3Decorator)
                }
            }
        }
    }

    private convertProperties(value: Type, conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>, ret: ObjectC3Type) {
        const properties = value.getProperties()
        for (const property of properties) {
            const propertyName = property.getName()

            conversionContext.beginProcessingProperty(propertyName)

            if (!conversionContext.state().shouldExclude(conversionContext.currentJsonPath)) {
                let propertyDefinition: PropertyDefinition | null = null

                const override = conversionContext.state().getOverrideType(conversionContext.currentJsonPath)
                if (override) {
                    propertyDefinition = override
                } else {
                    const transform = conversionContext.state().getTransformFunction(conversionContext.currentJsonPath)
                    if (transform) {
                        const converted = conversionContext.convert(transform.getReturnType())
                        propertyDefinition = new PropertyDefinition(propertyName, converted)
                    } else {
                        propertyDefinition = this.convertProperty(property, propertyName, conversionContext)
                    }
                }
                ret.addPropertyDefinition(propertyDefinition);
            }

            conversionContext.endProcessingProperty()
        }
    }

    private convertProperty(property: Symbol,
                            propertyName: string,
                            conversionContext: IConversionContext<Type, C3Type, TypescriptConversionState>): PropertyDefinition {
        const valueDeclaration = property.getValueDeclarationOrThrow("No value declaration could be found for property " + propertyName)

        let propertyDefinition: PropertyDefinition

        if(valueDeclaration.getType().isUnion()){
            conversionContext.state().unionPropertyNameStack.push(propertyName)
        }

        // Typescript cannot detect that this can be a DecoratableNode, so we have to cast it twice
        const decoratableNode = valueDeclaration as unknown as DecoratableNode

        if(decoratableNode && decoratableNode.getDecorators) { // decoratableNode.getDecorators checks for existence of function, which will only be present if it is a DecoratableNode

            const precisionDecorator = decoratableNode?.getDecorator('Precision')
            if (precisionDecorator) {
                // TODO: Verify this is a number type. This could also be a union type that has a number in the case of something like
                // public myNumber: number | null
                // or public myNumber?: number
                // Additionally, this should be moved to the standard decorator pattern
                const converted = convertPrecisionToC3Type(precisionDecorator)
                propertyDefinition = new PropertyDefinition(propertyName, converted)
            } else {
                const converted = conversionContext.convert(valueDeclaration.getType())
                propertyDefinition = new PropertyDefinition(propertyName, converted)
            }

            if (decoratableNode?.getDecorators()) {
                for (const decorator of decoratableNode.getDecorators()) {
                    // We have already handled Precision above
                    if (decorator.getName() !== 'Precision') {
                        const c3Decorator = tsDecoratorToC3Decorator(decorator)
                        if(c3Decorator) {

                            if(c3Decorator instanceof TenantIdDecorator){
                                conversionContext.state().multiTenantSelectionEnabled = true

                            }

                            if (!propertyDefinition.containsDecorator(c3Decorator)) {
                                propertyDefinition.addDecorator(c3Decorator)
                            }
                        }
                    }
                }
            }

        }else{
            const converted = conversionContext.convert(valueDeclaration.getType())
            propertyDefinition = new PropertyDefinition(propertyName, converted)
        }

        if(valueDeclaration.getType().isUnion()){
            conversionContext.state().unionPropertyNameStack.pop()
        }

        return propertyDefinition
    }
}
