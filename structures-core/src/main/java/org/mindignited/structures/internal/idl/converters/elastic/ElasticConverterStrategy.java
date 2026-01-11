package org.mindignited.structures.internal.idl.converters.elastic;

import co.elastic.clients.elasticsearch._types.mapping.*;
import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.idl.api.converter.C3ConversionContext;
import org.kinotic.continuum.idl.api.converter.C3TypeConverter;
import org.kinotic.continuum.idl.api.converter.C3TypeConverterContainer;
import org.kinotic.continuum.idl.api.converter.IdlConverterStrategy;
import org.kinotic.continuum.idl.api.schema.*;
import org.mindignited.structures.api.config.StructuresProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Strategy for converting C3 types to ES properties
 * Created by Navíd Mitchell 🤪 on 4/28/23.
 */
@RequiredArgsConstructor
public class ElasticConverterStrategy implements IdlConverterStrategy<Property, ElasticConversionState> {

    private static final Property BOOL = BooleanProperty.of(f -> f)._toProperty();
    private static final Property BOOL_NO_INDEX 
        = BooleanProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property BYTE = ByteNumberProperty.of(f -> f)._toProperty();
    private static final Property BYTE_NO_INDEX 
        = ByteNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property KEYWORD = KeywordProperty.of(f -> f)._toProperty();
    private static final Property KEYWORD_NO_INDEX 
        = KeywordProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property DATE = DateProperty.of(f -> f)._toProperty();
    private static final Property DATE_NO_INDEX 
        = DateProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property DOUBLE = DoubleNumberProperty.of(f -> f)._toProperty();
    private static final Property DOUBLE_NO_INDEX 
        = DoubleNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property FLOAT = FloatNumberProperty.of(f -> f)._toProperty();
    private static final Property FLOAT_NO_INDEX 
        = FloatNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property INT = IntegerNumberProperty.of(f -> f)._toProperty();
    private static final Property INT_NO_INDEX 
        = IntegerNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property LONG = LongNumberProperty.of(f -> f)._toProperty();
    private static final Property LONG_NO_INDEX 
        = LongNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Property SHORT = ShortNumberProperty.of(f -> f)._toProperty();
    private static final Property SHORT_NO_INDEX 
        = ShortNumberProperty.of(f -> f.index(false).docValues(false))._toProperty();

    private static final Set<C3TypeConverter<Property, ? extends C3Type, ElasticConversionState>> converters;
    private final StructuresProperties structuresProperties;

    static {
        // Basic types
        C3TypeConverterContainer<Property, ElasticConversionState> container = new C3TypeConverterContainer<>();
        container.addConverter(BooleanC3Type.class, primitiveConverter(BOOL, BOOL_NO_INDEX))
                 .addConverter(ByteC3Type.class, primitiveConverter(BYTE, BYTE_NO_INDEX))
                 .addConverter(CharC3Type.class, primitiveConverter(KEYWORD, KEYWORD_NO_INDEX))
                 .addConverter(DoubleC3Type.class, primitiveConverter(DOUBLE, DOUBLE_NO_INDEX))
                 .addConverter(FloatC3Type.class, primitiveConverter(FLOAT, FLOAT_NO_INDEX))
                 .addConverter(IntC3Type.class, primitiveConverter(INT, INT_NO_INDEX))
                 .addConverter(LongC3Type.class, primitiveConverter(LONG, LONG_NO_INDEX))
                 .addConverter(ShortC3Type.class, primitiveConverter(SHORT, SHORT_NO_INDEX))
                 .addConverter(StringC3Type.class, primitiveConverter(KEYWORD, KEYWORD_NO_INDEX))
                 .addConverter(DateC3Type.class, primitiveConverter(DATE, DATE_NO_INDEX))
                 .addConverter(EnumC3Type.class, primitiveConverter(KEYWORD, KEYWORD_NO_INDEX))
                 .addConverter(ArrayC3Type.class, (arrayC3Type, context) -> {
                     // There is no specific type for arrays in elastic search, so we just convert the inner type
                     return context.convert(arrayC3Type.getContains());
                 });

        converters = new LinkedHashSet<>(List.of(container, new ObjectC3TypeToElastic(), new UnionC3TypeToElastic()));
    }

    private static <T extends C3Type> BiFunction<T, C3ConversionContext<Property, ElasticConversionState>, Property>
    primitiveConverter(Property indexedProperty, Property notIndexedProperty) {
        return (c3Type, context) -> {
            if(context.state().isShouldIndex()){
                return indexedProperty;
            } else {
                return notIndexedProperty;
            }
        };
    }


    @Override
    public Set<C3TypeConverter<Property, ? extends C3Type, ElasticConversionState>> converters() {
        return converters;
    }

    @Override
    public ElasticConversionState initialState() {
        return new ElasticConversionState(this.structuresProperties);
    }

    @Override
    public boolean shouldCache() {
        return true;
    }

}
