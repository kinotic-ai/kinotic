import {C3Type} from '@kinotic/continuum-idl'
import {Logger} from '../../Logger.js'
import {IConverterStrategy} from '../IConverterStrategy.js'
import {GraphQLType} from 'graphql/type/index.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {GqlArrayToC3Type} from './GqlArrayToC3Type.js'
import {GqlConversionState} from './GqlConversionState.js'
import {GqlEnumToC3Type} from './GqlEnumToC3Type.js'
import {GqlObjectToC3Type} from './GqlObjectToC3Type.js'
import {GqlPrimitiveToC3Type} from './GqlPrimitiveToC3Type.js'
import {GqlUnionToC3Type} from './GqlUnionToC3Type.js'

export class GqlConverterStrategy implements IConverterStrategy<GraphQLType, C3Type, GqlConversionState> {

    private readonly _initialState: (() => GqlConversionState) | GqlConversionState
    private readonly _logger: Logger
    private readonly _typeConverters = [
        new GqlPrimitiveToC3Type(),
        new GqlEnumToC3Type(),
        new GqlArrayToC3Type(),
        new GqlObjectToC3Type(),
        new GqlUnionToC3Type()
    ]


    constructor(initialState: (() => GqlConversionState) | GqlConversionState, logger: Logger) {
        this._initialState = initialState
        this._logger = logger
    }


    initialState(): (() => GqlConversionState) | GqlConversionState {
        return this._initialState
    }

    logger(): Logger | (() => Logger) {
        return this._logger
    }

    typeConverters(): Array<ITypeConverter<GraphQLType, C3Type, GqlConversionState>> {
        return this._typeConverters
    }

    valueToString(value: GraphQLType): string {
        return value.toString()
    }

}
