package org.kinotic.core.api;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.core.internal.api.service.rpc.DefaultRpcServiceProxyHandle;
import org.kinotic.core.internal.api.service.rpc.RpcArgumentConverterResolver;
import org.kinotic.core.internal.api.service.rpc.RpcProxyServices;
import org.springframework.util.MimeTypeUtils;

/**
 * The service proxies minted over one transport: each sends its requests through it, watches them with its liveness
 * watcher and names the process in its reply destination. A cluster member's {@link ServiceRegistry} hands out the
 * proxies over the event bus; a client hands out those over its gateway connection.
 *
 * Created by Navíd Mitchell 🤪 on 11/7/26.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RpcServiceProxies {

    private final RpcArgumentConverterResolver argumentConverters;
    private final RpcProxyServices services;

    /**
     * Creates a proxy for the service the interface addresses, sending its arguments as JSON.
     * @param serviceInterface the interface of the service, annotated with {@link org.kinotic.core.api.annotations.Proxy}
     *                         or {@link org.kinotic.core.api.annotations.Publish}
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle} that gives access to the proxy and releases it
     */
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        return serviceProxy(KinoticUtil.serviceIdentifierOf(serviceInterface), serviceInterface);
    }

    /**
     * Creates a proxy for the service identified, implementing the interface and sending its arguments as JSON.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface  the interface that the service implements and the target class of the returned proxy
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle} that gives access to the proxy and releases it
     */
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        return serviceProxy(serviceIdentifier, serviceInterface, MimeTypeUtils.APPLICATION_JSON_VALUE);
    }

    /**
     * Creates a proxy for the service identified, implementing the interface and sending its arguments as the
     * content type.
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface  the interface that the service implements and the target class of the returned proxy
     * @param contentType       the content type the arguments are sent as, which a converter of the process must
     *                          produce
     * @param <T> the type of the serviceInterface
     * @return the {@link RpcServiceProxyHandle} that gives access to the proxy and releases it
     */
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface, String contentType) {
        Validate.notBlank(contentType, "contentType must not be blank");
        Validate.isTrue(argumentConverters.canResolve(contentType),
                        "The contentType:" + contentType + " does not have any configured RpcArgumentConverter's");
        return new DefaultRpcServiceProxyHandle<>(serviceIdentifier,
                                                  serviceInterface,
                                                  argumentConverters.resolve(contentType),
                                                  serviceInterface.getClassLoader(),
                                                  services);
    }

}
