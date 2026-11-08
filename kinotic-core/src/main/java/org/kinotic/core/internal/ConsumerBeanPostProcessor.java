package org.kinotic.core.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.RpcServiceProxy;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.internal.api.event.ConsumerMethodRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers the {@link Consumer} methods of each bean with the {@link ConsumerMethodRegistry}, so they receive the
 * events of their type, and removes them when the bean is destroyed.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    private static final MethodFilter CONSUMER_METHODS = method -> !method.isSynthetic() && method.isAnnotationPresent(Consumer.class);

    // Resolved through a provider rather than injected directly: a BeanPostProcessor's constructor
    // dependencies are instantiated during the post-processor registration phase, which would drag
    // EventBusService and Vertx up before the rest of the context and leave them ineligible for
    // later post-processors. The first bean carrying consumer methods materializes the registry during
    // ordinary singleton initialization instead.
    private final ObjectProvider<ConsumerMethodRegistry> registryProvider;

    // Cached on first registration; null means the registry was never created, so no bean is registered
    private volatile ConsumerMethodRegistry registry;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // RpcServiceProxies are framework plumbing, mirror ServiceRegistrationBeanPostProcessor's guard
        if(!(bean instanceof RpcServiceProxy)){
            List<Method> methods = new ArrayList<>();
            ReflectionUtils.doWithMethods(bean.getClass(), methods::add, CONSUMER_METHODS);
            for(Method method : methods){
                try {
                    // Blocking until registration completes makes startup ordering deterministic,
                    // the same trade ServiceRegistrationBeanPostProcessor makes for service registration
                    registry().register(bean, method)
                              .toCompletionStage()
                              .toCompletableFuture()
                              .join();
                } catch (IllegalArgumentException e) {
                    throw new FatalBeanException("Invalid @Consumer method on bean " + beanName, e);
                } catch (Exception e) {
                    throw new FatalBeanException("Failed to register the @Consumer methods of bean " + beanName, e);
                }
            }
        }
        return bean;
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        ConsumerMethodRegistry current = registry;
        return current != null && current.isRegistered(bean);
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        // Only reached for beans requiresDestruction accepted, so the registry exists
        registry.unregister(bean);
    }

    private ConsumerMethodRegistry registry() {
        ConsumerMethodRegistry current = registry;
        if(current == null){
            current = registryProvider.getObject();
            registry = current;
        }
        return current;
    }
}
