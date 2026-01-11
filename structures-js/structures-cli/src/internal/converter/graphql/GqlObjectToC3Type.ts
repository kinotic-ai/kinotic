import {GraphQLType, GraphQLObjectType, GraphQLField, GraphQLNonNull} from 'graphql/type/index.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {GqlConversionState} from './GqlConversionState.js'
import {ObjectC3Type, C3Type, NotNullDecorator} from '@mindignited/continuum-idl'

export class GqlObjectToC3Type implements ITypeConverter<GraphQLType, C3Type, GqlConversionState> {

    convert(value: GraphQLType, conversionContext: IConversionContext<GraphQLType, C3Type, GqlConversionState>): C3Type {
        if (value instanceof GraphQLObjectType) {
            const name = value.name
            const namespace = conversionContext.state().application
            const objectC3Type = new ObjectC3Type(name, namespace)

            const fields = value.getFields()
            for (const fieldName in fields) {
                const field: GraphQLField<any, any> = fields[fieldName]
                let fieldType = field.type
                let isNonNull = false
                if(fieldType instanceof GraphQLNonNull){
                    fieldType = (fieldType as GraphQLNonNull<any>).ofType
                    isNonNull = true
                }
                objectC3Type.addProperty(fieldName, conversionContext.convert(fieldType), (isNonNull ? [new NotNullDecorator()] : undefined))
            }

            return objectC3Type
        }
        throw new Error('GraphQLObjectType expected, got type: : ' + value.toString())
    }

    supports(value: GraphQLType, conversionState: GqlConversionState): boolean {
        return value instanceof GraphQLObjectType
    }
}
