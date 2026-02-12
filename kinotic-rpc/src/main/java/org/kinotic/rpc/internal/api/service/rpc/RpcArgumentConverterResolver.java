

package org.kinotic.rpc.internal.api.service.rpc;

/**
 *
 * Created by Navid Mitchell on 6/23/20
 */
public interface RpcArgumentConverterResolver {

    /**
     * Determines if a valid {@link RpcArgumentConverter} can be resolved for the given contentType
     * @param contentType contentType that the must be produced by the resolved {@link RpcArgumentConverter}
     * @return true if there is a valid {@link RpcArgumentConverter} for the contentType false if not
     */
    boolean canResolve(String contentType);

    /**
     * Resolve the correct {@link RpcArgumentConverter} for the desired contentType
     * @param contentType that the must be produced by the resolved {@link RpcArgumentConverter}
     * @return the correct {@link RpcArgumentConverter} for the content type
     */
    RpcArgumentConverter resolve(String contentType);

}
