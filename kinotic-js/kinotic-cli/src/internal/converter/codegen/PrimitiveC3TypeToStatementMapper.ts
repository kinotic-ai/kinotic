import {
    BooleanC3Type,
    ByteC3Type,
    C3Type,
    CharC3Type,
    DateC3Type,
    DoubleC3Type, EnumC3Type, FloatC3Type, IntC3Type, LongC3Type,
    ShortC3Type, StringC3Type
} from '@kinotic/continuum-idl'
import {IConversionContext} from '../IConversionContext.js'
import {ITypeConverter} from '../ITypeConverter.js'
import {LiteralStatementMapper, StatementMapper} from './StatementMapper.js'
import {StatementMapperConversionState} from './StatementMapperConversionState.js'

export class PrimitiveC3TypeToStatementMapper implements ITypeConverter<C3Type, StatementMapper, StatementMapperConversionState> {

    convert(value: C3Type, conversionContext: IConversionContext<C3Type, StatementMapper, StatementMapperConversionState>): StatementMapper {
        const targetName = conversionContext.state().targetName
        const sourceName = conversionContext.state().sourceName
        const lhs = targetName + (conversionContext.currentJsonPath.length > 0 ? '.' + conversionContext.currentJsonPath : '')
        const rhs = sourceName + (conversionContext.currentJsonPath.length > 0 ? '.' + conversionContext.currentJsonPath : '')
        return new LiteralStatementMapper(`${lhs} = ${rhs}`)
    }

    supports(value: C3Type, conversionState: StatementMapperConversionState): boolean {
        return (value instanceof BooleanC3Type || value.type === 'boolean')
            || (value instanceof ByteC3Type || value.type === 'byte')
            || (value instanceof CharC3Type || value.type === 'char')
            || (value instanceof DateC3Type || value.type === 'date')
            || (value instanceof DoubleC3Type || value.type === 'double')
            || (value instanceof EnumC3Type || value.type === 'enum')
            || (value instanceof FloatC3Type || value.type === 'float')
            || (value instanceof IntC3Type || value.type === 'int')
            || (value instanceof LongC3Type || value.type === 'long')
            || (value instanceof ShortC3Type || value.type === 'short')
            || (value instanceof StringC3Type || value.type === 'string')
    }
}
