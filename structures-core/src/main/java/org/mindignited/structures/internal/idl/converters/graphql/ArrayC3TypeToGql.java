package org.mindignited.structures.internal.idl.converters.graphql;

import graphql.schema.GraphQLList;
import org.mindignited.continuum.idl.api.converter.C3ConversionContext;
import org.mindignited.continuum.idl.api.converter.C3TypeConverter;
import org.mindignited.continuum.idl.api.schema.ArrayC3Type;
import org.mindignited.continuum.idl.api.schema.C3Type;

/**
 * Created By Navíd Mitchell 🤪on 2/26/25
 */
public class ArrayC3TypeToGql implements C3TypeConverter<GqlTypeHolder, ArrayC3Type, GqlConversionState> {
    @Override
    public boolean supports(C3Type c3Type) {
        return c3Type instanceof ArrayC3Type;
    }

    @Override
    public GqlTypeHolder convert(ArrayC3Type c3Type, C3ConversionContext<GqlTypeHolder, GqlConversionState> context) {
        // TODO: How do we want to specify that Nulls are allowed of not?
        //       GQL allows the schema to specify this. However, we do not have a similar concept in C3.
        GqlTypeHolder typeHolder = context.convert(c3Type.getContains());

        // input type can be null in some cases such as a Union type.
        // This can create a paradigm mismatch between OpenApi and GraphQL, but we cannot do anything about it.
        // For now, we will not create an input type for these cases.
        return new GqlTypeHolder(typeHolder.inputType() != null ? GraphQLList.list(typeHolder.inputType()) : null,
                                 GraphQLList.list(typeHolder.outputType()));
    }
}
