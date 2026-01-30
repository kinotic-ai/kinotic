import {GraphQLType} from 'graphql/type/index.js'
import {SpecificTypesConverter} from '../SpecificTypesConverter.js'
import {IConversionContext} from '../IConversionContext.js'
import {GqlConversionState} from './GqlConversionState.js'
import {BooleanC3Type, StringC3Type, IntC3Type, FloatC3Type, C3Type} from '@kinotic/continuum-idl'

export class GqlPrimitiveToC3Type extends SpecificTypesConverter<GraphQLType, C3Type, GqlConversionState, string> {

    constructor() {
        const map: Map<string, (type: GraphQLType, context: IConversionContext<GraphQLType, C3Type, GqlConversionState>) => C3Type> = new Map()

        map.set('String', () => new StringC3Type())
        map.set('Boolean', () => new BooleanC3Type())
        map.set('Int', () => new IntC3Type())
        map.set('Float', () => new FloatC3Type())
        map.set('ID', () => new StringC3Type())

        super(
            (arg: GraphQLType) => arg.toString(),
            map
        )
    }
}
