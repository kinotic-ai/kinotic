

package org.kinotic.rpc.gateway.internal.endpoints.rest;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.Validate;
import org.kinotic.rpc.api.event.CRI;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.Metadata;
import org.kinotic.rpc.internal.api.event.MultiMapMetadataAdapter;

import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * Created by navid on 12/19/19
 */
class RoutingContextEventAdapter implements Event<byte[]> {

    private final CRI cri;
    private final MultiMapMetadataAdapter metadataAdapter;
    private final RoutingContext routingContext;

    public RoutingContextEventAdapter(String rootPath, RoutingContext routingContext) {
        Validate.notBlank(rootPath,"The rootPath must not be blank");
        Validate.notNull(routingContext, "The RoutingContext must not be null");
        Validate.notNull(routingContext.request(), "RoutingContext.request() must not be null");

        this.routingContext = routingContext;
        // remove headers we do not want sent around..
        routingContext.request().headers().remove(HttpHeaders.AUTHORIZATION);
        this.metadataAdapter = new MultiMapMetadataAdapter(routingContext.request().headers());

        // We adapt the CRI information to the expectations of the current Service Invoker
        // Path provided will be like ex:
        // http://localhost/api/srv/org.kinotic.testapplication.services.TestService/getFreeMemory

        String pathWithoutRoot = Strings.CS.removeStart(routingContext.request().path(), rootPath);
        Validate.notBlank(pathWithoutRoot, "Path must be provided and point to a valid service");
        pathWithoutRoot = pathWithoutRoot.substring(1); // remove leading slash
        pathWithoutRoot = pathWithoutRoot.replaceFirst("/","://");

        this.cri = CRI.create(pathWithoutRoot);
    }

    @Override
    public CRI cri() {
        return cri;
    }

    @Override
    public Metadata metadata() {
        return metadataAdapter;
    }

    @Override
    public byte[] data() {
        return routingContext.body().buffer().getBytes();
    }

}
