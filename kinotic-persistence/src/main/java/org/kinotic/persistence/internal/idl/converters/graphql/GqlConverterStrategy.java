package org.kinotic.persistence.internal.idl.converters.graphql;

import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLEnumType;
import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.idl.api.converter.C3TypeConverter;
import org.kinotic.continuum.idl.api.converter.C3TypeConverterContainer;
import org.kinotic.continuum.idl.api.converter.IdlConverterStrategy;
import org.kinotic.continuum.idl.api.schema.*;
import org.kinotic.persistence.api.config.StructuresProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLTypeReference.typeRef;

/**
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
@RequiredArgsConstructor
public class GqlConverterStrategy implements IdlConverterStrategy<GqlTypeHolder, GqlConversionState> {

    private static final GqlTypeHolder BOOL = new GqlTypeHolder(GraphQLBoolean, GraphQLBoolean);
    private static final GqlTypeHolder BYTE = new GqlTypeHolder(ExtendedScalars.GraphQLByte, ExtendedScalars.GraphQLByte);
    private static final GqlTypeHolder CHAR = new GqlTypeHolder(ExtendedScalars.GraphQLChar, ExtendedScalars.GraphQLChar);
    private static final GqlTypeHolder DATE = new GqlTypeHolder(ExtendedScalars.DateTime, ExtendedScalars.DateTime);
    private static final GqlTypeHolder DOUBLE = new GqlTypeHolder(GraphQLFloat, GraphQLFloat);
    private static final GqlTypeHolder FLOAT = new GqlTypeHolder(GraphQLFloat, GraphQLFloat);
    private static final GqlTypeHolder INT = new GqlTypeHolder(GraphQLInt, GraphQLInt);
    private static final GqlTypeHolder LONG = new GqlTypeHolder(ExtendedScalars.GraphQLLong, ExtendedScalars.GraphQLLong);
    private static final GqlTypeHolder SHORT = new GqlTypeHolder(ExtendedScalars.GraphQLShort, ExtendedScalars.GraphQLShort);
    private static final GqlTypeHolder STRING = new GqlTypeHolder(GraphQLString, GraphQLString);
    private static final Set<C3TypeConverter<GqlTypeHolder, ? extends C3Type, GqlConversionState>> converters;
    private final StructuresProperties structuresProperties;

    static {
        C3TypeConverterContainer<GqlTypeHolder, GqlConversionState> container = new C3TypeConverterContainer<>();
        container.addConverter(BooleanC3Type.class, (c3Type, context) -> BOOL)
                 .addConverter(ByteC3Type.class, (c3Type, context) -> BYTE)
                 .addConverter(CharC3Type.class, (c3Type, context) -> CHAR)
                 .addConverter(DoubleC3Type.class, (c3Type, context) -> DOUBLE)
                 .addConverter(FloatC3Type.class, (c3Type, context) -> FLOAT)
                 .addConverter(IntC3Type.class, (c3Type, context) -> INT)
                 .addConverter(LongC3Type.class, (c3Type, context) -> LONG)
                 .addConverter(ShortC3Type.class, (c3Type, context) -> SHORT)
                 .addConverter(StringC3Type.class, (c3Type, context) -> STRING)
                 .addConverter(DateC3Type.class, (c3Type, context) -> DATE)
                 // Enum type
                 .addConverter(EnumC3Type.class, (c3Type, context) -> {
                     GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum();
                     String enumTypeName = c3Type.getName();
                     builder.name(enumTypeName);

                     for (String value : c3Type.getValues()) {
                         builder.value(value);
                     }

                     GraphQLEnumType type = builder.build();
                     context.state().getReferencedTypes().put(enumTypeName, type);

                     return new GqlTypeHolder(typeRef(enumTypeName), typeRef(enumTypeName));
                 });
        converters = new LinkedHashSet<>(List.of(container,
                                                 new ArrayC3TypeToGql(),
                                                 new ObjectC3TypeToGql(),
                                                 new UnionC3TypeToGql(),
                                                 new CursorPageC3TypeToGql(),
                                                 new PageC3TypeToGql()));
    }

    @Override
    public Set<C3TypeConverter<GqlTypeHolder, ? extends C3Type, GqlConversionState>> converters() {
        return converters;
    }

    @Override
    public GqlConversionState initialState() {
        return new GqlConversionState(this.structuresProperties);
    }

    @Override
    public boolean shouldCache() {
        return true;
    }

}
