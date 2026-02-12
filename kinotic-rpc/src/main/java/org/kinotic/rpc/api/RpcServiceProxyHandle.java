

package org.kinotic.rpc.api;

/**
 * {@link RpcServiceProxyHandle} provides access to a Service proxy.
 *
 *
 * Created by navid on 2019-04-18.
 */
public interface RpcServiceProxyHandle<T> {

    /**
     * Provides access to the service proxy instance managed by this {@link RpcServiceProxyHandle}
     * @return the service proxy instance
     */
    T getService();

    /**
     * Should be called when service will no longer be used
     */
    void release();

}
