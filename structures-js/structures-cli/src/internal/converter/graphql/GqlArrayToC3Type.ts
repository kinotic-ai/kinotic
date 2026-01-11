import {GraphQLType, GraphQLList, GraphQLNonNull} from 'graphql/type/index.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {GqlConversionState} from './GqlConversionState.js'
import {ArrayC3Type, C3Type} from '@mindignited/continuum-idl'

export class GqlArrayToC3Type implements ITypeConverter<GraphQLType, C3Type, GqlConversionState> {

    convert(value: GraphQLType, conversionContext: IConversionContext<GraphQLType, C3Type, GqlConversionState>): C3Type {
        if (value instanceof GraphQLList) {
            let valueType = value.ofType
            // unwrap non null
            // TODO: add a way to denote this for structures
            if(valueType instanceof GraphQLNonNull){
                valueType = (valueType as GraphQLNonNull<any>).ofType
            }
            const elementType = conversionContext.convert(valueType)
            return new ArrayC3Type(elementType)
        }
        throw new Error('GraphQLList expected, got type: ' + value.toString())
    }

    supports(value: GraphQLType, conversionState: GqlConversionState): boolean {
        return value instanceof GraphQLList
    }
}
