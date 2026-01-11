package org.mindignited.structures.internal.endpoints.graphql;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Delegates to the correct Vertx {@link io.vertx.ext.web.handler.graphql.GraphQLHandler} based on the path of the route
 * Created by Navíd Mitchell 🤪 on 11/19/24.
 */
public interface DelegatingGqlHandler extends Handler<RoutingContext> {


}
