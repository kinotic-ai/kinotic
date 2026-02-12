

package org.kinotic.rpc.internal.api.aignite;

import org.apache.commons.lang3.Validate;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

/**
 * Provides functionality to deploy an Ignite {@link Service} that configures a new Class instance using spring
 *
 * Created by navid on 10/15/19
 */
public class IgniteServiceAdapter implements Service {

    private final String serviceIdentifier;
    private final Class<?> serviceClass;
    private final Object[] constructorArgs;
    private Object serviceInstance;

    @SpringApplicationContextResource
    protected ApplicationContext applicationContext;

    public IgniteServiceAdapter(String serviceIdentifier,
                                Class<?> serviceClass,
                                Object[] constructorArgs) {
        Validate.notBlank(serviceIdentifier,"The serviceIdentifier provided must not be blank");
        Validate.notNull(serviceClass, "The service class provided must not be null");
        this.serviceIdentifier = serviceIdentifier;
        this.serviceClass = serviceClass;
        this.constructorArgs = constructorArgs;
    }

    @Override
    public void cancel(ServiceContext ctx) {
        // destroy instance using Spring Goodness!!
        if(serviceInstance != null) {
            applicationContext.getAutowireCapableBeanFactory().destroyBean(serviceInstance);
            serviceInstance = null;
        }
    }

    @Override
    public void init(ServiceContext ctx) throws Exception {
        // Create a new instance of the desired class using the arguments provided
        Class<?>[] argTypes = Arrays.stream(constructorArgs)
                                    .map(Object::getClass)
                                    .toArray(Class[]::new);

        Object instance = serviceClass.getDeclaredConstructor(argTypes).newInstance(constructorArgs);

        // Now wire instance and initialize it using Spring Goodness!!
        applicationContext.getAutowireCapableBeanFactory().autowireBean(instance);
        serviceInstance = applicationContext.getAutowireCapableBeanFactory().initializeBean(instance, serviceIdentifier);
    }

    @Override
    public void execute(ServiceContext ctx) throws Exception {
        Thread.sleep(300000);
    }

}
