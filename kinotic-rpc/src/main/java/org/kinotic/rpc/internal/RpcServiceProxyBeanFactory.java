

package org.kinotic.rpc.internal;

import org.kinotic.rpc.api.RpcServiceProxyHandle;
import org.kinotic.rpc.api.ServiceRegistry;
import org.kinotic.rpc.api.service.ServiceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;


/**
 * Creates a {@link RpcServiceProxyHandle} for the given serviceInterfaceClassName when needed by spring.
 * Automatically destroys the {@link RpcServiceProxyHandle} when the context goes out of scope.
 *
 *
 * Created by Navid Mitchell on 04/17/19.
 */
public class RpcServiceProxyBeanFactory extends AbstractFactoryBean<Object> {

    private final Class<?> serviceClass;
    private final ServiceIdentifier serviceIdentifier;

    private RpcServiceProxyHandle<?> serviceHandle;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ServiceRegistry serviceRegistry;


    public RpcServiceProxyBeanFactory(Class<?> serviceClass) {
        this(serviceClass, null);
        setSingleton(true);
    }

    public RpcServiceProxyBeanFactory(Class<?> serviceClass,
                                      ServiceIdentifier serviceIdentifier) {
        this.serviceClass = serviceClass;
        this.serviceIdentifier = serviceIdentifier;
        setSingleton(true);
    }

    @Override
    public synchronized Class<?> getObjectType() {
        return serviceClass;
    }

    @Override
    protected synchronized Object createInstance() {
        if(serviceHandle == null){
            if(serviceIdentifier == null) {
                serviceHandle = serviceRegistry.serviceProxy(getObjectType());
            }else{
                serviceHandle = serviceRegistry.serviceProxy(serviceIdentifier, getObjectType());
            }
        }
        return serviceHandle.getService();
    }

    @Override
    protected synchronized void destroyInstance(Object instance) throws Exception {
        if(serviceHandle != null){
            serviceHandle.release();
        }
    }
}
