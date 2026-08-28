package org.kinotic.core.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.RpcServiceProxy;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.core.internal.api.fabric.EventFabric;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires beans into the {@link EventFabric}: {@link Emitter} methods are subscribed and published
 * cluster-wide, {@link Consumer} methods are registered to receive events, and both are torn down
 * when the bean is destroyed.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventFabricBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    private static final MethodFilter FABRIC_METHODS = method ->
            !method.isSynthetic()
                    && (method.isAnnotationPresent(Emitter.class) || method.isAnnotationPresent(Consumer.class));

    private final EventFabric eventFabric;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // RpcServiceProxies are framework plumbing, mirror ServiceRegistrationBeanPostProcessor's guard
        if(!(bean instanceof RpcServiceProxy)){
            List<Method> methods = new ArrayList<>();
            ReflectionUtils.doWithMethods(bean.getClass(), methods::add, FABRIC_METHODS);

            for(Method method : methods){
                if(method.isAnnotationPresent(Emitter.class) && method.isAnnotationPresent(Consumer.class)){
                    throw new FatalBeanException("A method may not be both @Emitter and @Consumer: " + method);
                }
                try {
                    if(method.isAnnotationPresent(Emitter.class)){
                        eventFabric.wireEmitter(bean, method);
                    }else{
                        // Blocking until registration completes makes startup ordering deterministic,
                        // the same trade ServiceRegistrationBeanPostProcessor makes for service registration
                        eventFabric.wireConsumer(bean, method)
                                   .toCompletionStage()
                                   .toCompletableFuture()
                                   .join();
                    }
                } catch (IllegalArgumentException e) {
                    throw new FatalBeanException("Invalid event fabric wiring on bean " + beanName, e);
                } catch (FatalBeanException e) {
                    throw e;
                } catch (Exception e) {
                    throw new FatalBeanException("Failed to wire bean " + beanName + " into the event fabric", e);
                }
            }
        }
        return bean;
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        return eventFabric.isWired(bean);
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        eventFabric.unwire(bean);
    }
}
