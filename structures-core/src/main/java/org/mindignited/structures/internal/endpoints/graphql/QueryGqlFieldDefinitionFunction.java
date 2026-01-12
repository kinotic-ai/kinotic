package org.mindignited.structures.internal.endpoints.graphql;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLOutputType;
import lombok.RequiredArgsConstructor;
import org.mindignited.continuum.idl.api.schema.FunctionDefinition;
import org.mindignited.continuum.idl.api.schema.ParameterDefinition;
import org.mindignited.structures.api.domain.idl.CursorPageC3Type;
import org.mindignited.structures.api.domain.idl.PageC3Type;
import org.mindignited.structures.api.domain.idl.PageableC3Type;
import org.mindignited.structures.api.domain.idl.decorators.PolicyDecorator;
import org.mindignited.structures.internal.idl.converters.graphql.GqlTypeHolder;
import org.mindignited.structures.internal.utils.GqlUtils;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;

/**
 * This class is responsible for creating a {@link GraphQLFieldDefinition} for a query function
 * Created by Navíd Mitchell 🤪 on 6/13/24.
 */
@RequiredArgsConstructor
public class QueryGqlFieldDefinitionFunction implements GqlFieldDefinitionFunction {

    private final FunctionDefinition queryDefinition;
    private final boolean shouldUseCursorPageable;

    @Override
    public GraphQLFieldDefinition apply(GqlFieldDefinitionData data) {
        GraphQLFieldDefinition.Builder builder
                = newFieldDefinition().name(queryDefinition.getName() + data.getStructureName());

        PolicyDecorator policyDecorator = queryDefinition.findDecorator(PolicyDecorator.class);
        if(policyDecorator != null){
            builder = builder.withDirective(GqlUtils.policy(policyDecorator.getPolicies()));
        }

        // This is kinda a hack. The CLI will always just create a PageC3Type, but if a cursor is used it needs to explicitly defined for GQL
        GqlTypeHolder retTypeHolder;
        if(shouldUseCursorPageable &&
                (queryDefinition.getReturnType() instanceof PageC3Type pageC3Type)){

            CursorPageC3Type cursorPageC3Type = new CursorPageC3Type(pageC3Type.getContentType());

            retTypeHolder = data.getConverter().convert(cursorPageC3Type);
        }else{
            retTypeHolder = data.getConverter().convert(queryDefinition.getReturnType());
        }
        GraphQLOutputType outputType = retTypeHolder.outputType();


        // If the return type is an array we need to wrap in notNull objs.
        // The converter does not do this since this is not specified with a NotNull decorator
        if(outputType instanceof GraphQLList list){
            outputType = GraphQLList.list(nonNull(list.getWrappedType()));
            outputType = nonNull(outputType);
        }
        builder.type(outputType);

        for(ParameterDefinition parameter : queryDefinition.getParameters()){

            GraphQLInputType inputType;
            if(!(parameter.getType() instanceof PageableC3Type)) {
                GqlTypeHolder paramType = data.getConverter().convert(parameter.getType());
                inputType = paramType.inputType();
            }else{
                if(shouldUseCursorPageable){
                    inputType = data.getCursorPageableReference();
                }else{
                    inputType = data.getOffsetPageableReference();
                }
            }
            builder.argument(newArgument().name(parameter.getName())
                                          .type(nonNull(inputType)));
        }

        return builder.build();
    }
}

