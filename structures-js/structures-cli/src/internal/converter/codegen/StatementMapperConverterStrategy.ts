import {C3Type} from '@mindignited/continuum-idl'
import {Logger} from '../../Logger.js'
import {IConverterStrategy} from '../IConverterStrategy.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {ArrayC3TypeToStatementMapper} from './ArrayC3TypeToStatementMapper.js'
import {StatementMapper} from './StatementMapper.js'
import {StatementMapperConversionState} from './StatementMapperConversionState.js'
import {ObjectC3TypeToStatementMapper} from './ObjectC3TypeToStatementMapper.js'
import {PrimitiveC3TypeToStatementMapper} from './PrimitiveC3TypeToStatementMapper.js'
import {UnionC3TypeToStatementMapper} from './UnionC3TypeToStatementMapper.js'

export class StatementMapperConverterStrategy implements IConverterStrategy<C3Type, StatementMapper, StatementMapperConversionState>{

    private readonly _initialState: (() => StatementMapperConversionState) | StatementMapperConversionState
    private readonly _logger: Logger
    private readonly _typeConverters = [
        new PrimitiveC3TypeToStatementMapper(),
        new ArrayC3TypeToStatementMapper(),
        new ObjectC3TypeToStatementMapper(),
        new UnionC3TypeToStatementMapper()
    ]

    constructor(initialState: (() => StatementMapperConversionState) | StatementMapperConversionState, logger: Logger) {
        this._initialState = initialState
        this._logger = logger
    }

    initialState(): (() => StatementMapperConversionState) | StatementMapperConversionState {
        return this._initialState
    }

    logger(): Logger | (() => Logger) {
        return this._logger
    }

    typeConverters(): Array<ITypeConverter<C3Type, StatementMapper, StatementMapperConversionState>> {
        return this._typeConverters
    }

    valueToString(value: C3Type): string {
        return value.type
    }

}
