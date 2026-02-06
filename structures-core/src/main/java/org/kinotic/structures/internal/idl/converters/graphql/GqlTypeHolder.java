package org.kinotic.structures.internal.idl.converters.graphql;

import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;

/**
 * Holds the Graph input and output types created for a given C3 type
 * Created by Navíd Mitchell 🤪 on 5/2/23.
 *
 * @param inputType  The GraphQL input type for the C3 type
 * @param outputType The GraphQL output type for the C3 type
 */
public record GqlTypeHolder(GraphQLInputType inputType, GraphQLOutputType outputType) {

    public static GqlTypeHolderBuilder builder() {
        return new GqlTypeHolderBuilder();
    }

    public GqlTypeHolderBuilder toBuilder() {
        return new GqlTypeHolderBuilder().inputType(this.inputType).outputType(this.outputType);
    }

    public static class GqlTypeHolderBuilder {
        private GraphQLInputType inputType;
        private GraphQLOutputType outputType;

        GqlTypeHolderBuilder() {
        }

        public GqlTypeHolder build() {
            return new GqlTypeHolder(this.inputType, this.outputType);
        }

        public GqlTypeHolderBuilder inputType(GraphQLInputType inputType) {
            this.inputType = inputType;
            return this;
        }

        public GqlTypeHolderBuilder outputType(GraphQLOutputType outputType) {
            this.outputType = outputType;
            return this;
        }

        public String toString() {
            return "GqlTypeHolder.GqlTypeHolderBuilder(inputType=" + this.inputType + ", outputType=" + this.outputType + ")";
        }
    }
}
