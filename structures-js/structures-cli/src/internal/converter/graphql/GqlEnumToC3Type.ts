import {GraphQLType, GraphQLEnumType} from 'graphql/type/index.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {GqlConversionState} from './GqlConversionState.js'
import {EnumC3Type, C3Type} from '@kinotic/continuum-idl'

export class GqlEnumToC3Type implements ITypeConverter<GraphQLType, C3Type, GqlConversionState> {

    convert(value: GraphQLType, conversionContext: IConversionContext<GraphQLType, C3Type, GqlConversionState>): C3Type {
        if (value instanceof GraphQLEnumType) {
            const enumC3Type = new EnumC3Type(value.name, conversionContext.state().application)

            const values = value.getValues()
            for (const enumValue of values) {
                enumC3Type.addValue(enumValue.name)
            }

            return enumC3Type
        }
        throw new Error('GraphQLEnumType expected got type: ' + value.toString())
    }

    supports(value: GraphQLType, conversionState: GqlConversionState): boolean {
        return value instanceof GraphQLEnumType
    }
}
