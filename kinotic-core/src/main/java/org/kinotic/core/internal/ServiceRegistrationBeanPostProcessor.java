


package org.kinotic.core.internal;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.RpcServiceProxy;
import org.kinotic.core.api.ServiceRegistry;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.internal.utils.KinoticUtil;
import org.kinotic.core.internal.utils.MetaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Registers configured beans to participate in Continuum functionality
 *
 *
 * Created by Navid Mitchell on 11/28/18.
 */
@Component
@RequiredArgsConstructor
public class ServiceRegistrationBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrationBeanPostProcessor.class);

    private final ServiceRegistry serviceRegistry;
    private final ObjectProvider<ServiceDirectory> serviceDirectoryProvider;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        processBean(bean, (serviceIdentifier, clazz) -> {

            log.info("Registering Service {}", serviceIdentifier);

            try {
                serviceRegistry.register(serviceIdentifier, clazz, bean)
                               .toCompletionStage()
                               .toCompletableFuture()
                               .join();

                log.trace("Successfully Registered service {}", serviceIdentifier);
            } catch (Exception e) {
                // A service that is not actually serving must not advertise itself in the directory
                log.error("Error Registering service {}", serviceIdentifier, e);
                return;
            }

            // The directory is a secondary concern; a bad @McpTool annotation must not crash service registration.
            // With no directory bean present, nothing at all happens here.
            ServiceDirectory serviceDirectory = serviceDirectoryProvider.getIfAvailable();
            if (serviceDirectory != null) {
                try {
                    serviceDirectory.register(serviceIdentifier, clazz, bean.getClass());
                } catch (Exception e) {
                    log.error("Failed to register service {} in the ServiceDirectory", serviceIdentifier, e);
                }
            }
        });
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        processBean(bean, (serviceIdentifier, clazz) -> {

            log.info("Un-Registering Service {}", serviceIdentifier);

            try {
                serviceRegistry.unregister(serviceIdentifier)
                               .toCompletionStage()
                               .toCompletableFuture()
                               .join();

                log.trace("Successfully Un-Registered service {}", serviceIdentifier);
            } catch (Exception e) {
                log.error("Error Un-Registering service {}", serviceIdentifier, e);
            }

            ServiceDirectory serviceDirectory = serviceDirectoryProvider.getIfAvailable();
            if (serviceDirectory != null) {
                try {
                    serviceDirectory.unregister(serviceIdentifier, clazz, bean.getClass());
                } catch (Exception e) {
                    log.error("Failed to mark service {} offline in the ServiceDirectory", serviceIdentifier, e);
                }
            }
        });
    }

    private void processBean(Object instance, BiConsumer<ServiceIdentifier, Class<?>> consumer){
        // Do not wrap RpcServiceProxies with invokers. Infinite Recursion boom!
        if(!(instance instanceof RpcServiceProxy)) {
            try {
                // See if any of the interfaces have a @Publish annotation
                Class<?> clazz = instance.getClass();
                List<Class<?>> interfaces = MetaUtil.getInterfaceDeclaringAnnotation(clazz, Publish.class);

                if (!interfaces.isEmpty()) {

                    for (Class<?> inter : interfaces) {

                        Publish publish = AnnotationUtils.findAnnotation(inter, Publish.class);

                        if(publish != null) {
                            String namespace = publish.namespace().isEmpty()
                                    ? KinoticUtil.safeEncodeURI(inter.getPackageName())
                                    : KinoticUtil.safeEncodeURI(publish.namespace());

                            String name = publish.name().isEmpty() ? inter.getSimpleName() : publish.name();
                            String scope = MetaUtil.getScopeIfAvailable(instance, inter);
                            String version = MetaUtil.getVersion(inter);

                            if (!StringUtils.isNotBlank(version)) {
                                throw new FatalBeanException("Version must be specified on the Published interface " + inter.getName() + " or an ancestor package.");
                            }

                            // A service is addressable in its declared zone; with no declaration
                            // it registers a single un-zoned address, whose reachability is
                            // whatever the gateway's routing rules allow
                            String zone = MetaUtil.getZone(inter);
                            ServiceIdentifier serviceIdentifier = new ServiceIdentifier(zone,
                                                                                        namespace,
                                                                                        name,
                                                                                        scope,
                                                                                        version);

                            consumer.accept(serviceIdentifier, inter);

                        }else{
                            // Ths should never happen
                            throw new FatalBeanException("Publish scanning failed for bean:" + instance);
                        }
                    }
                }
            } catch (FatalBeanException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Error processing Meta for bean:{}", instance, e);
            }
        }
    }


}
