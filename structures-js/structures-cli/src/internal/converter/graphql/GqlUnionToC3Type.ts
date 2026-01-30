import {GraphQLType, GraphQLUnionType} from 'graphql/type/index.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {GqlConversionState} from './GqlConversionState.js'
import {UnionC3Type, ObjectC3Type, C3Type} from '@kinotic/continuum-idl'

export class GqlUnionToC3Type implements ITypeConverter<GraphQLType, C3Type, GqlConversionState> {

    convert(value: GraphQLType, conversionContext: IConversionContext<GraphQLType, C3Type, GqlConversionState>): C3Type {
        if (value instanceof GraphQLUnionType) {
            const unionC3Type = new UnionC3Type(value.name, conversionContext.state().application)

            const types = value.getTypes()
            unionC3Type.types = types.map(type => {
                const convertedType = conversionContext.convert(type)
                if (convertedType instanceof ObjectC3Type) {
                    return convertedType
                } else {
                    throw new Error(`Union type can only contain ObjectC3Type, got: ${type.toString()}`)
                }
            })

            return unionC3Type
        }
        throw new Error('GraphQLUnionType expected got type: ' + value.toString())
    }

    supports(value: GraphQLType, conversionState: GqlConversionState): boolean {
        return value instanceof GraphQLUnionType
    }
}
