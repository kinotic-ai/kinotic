package org.kinotic.core.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.RpcServiceProxy;
import org.kinotic.core.api.annotations.Consumer;
import org.kinotic.core.api.annotations.Emitter;
import org.kinotic.core.internal.api.event.fabric.EventFabric;
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

    // Resolved through a provider rather than injected directly: a BeanPostProcessor's constructor
    // dependencies are instantiated during the post-processor registration phase, which would drag
    // EventBusService and Vertx up before the rest of the context and leave them ineligible for
    // later post-processors. The first bean carrying fabric methods materializes the fabric during
    // ordinary singleton initialization instead.
    private final ObjectProvider<EventFabric> eventFabricProvider;

    // Cached on first wiring; null means the fabric was never created, so no bean is wired
    private volatile EventFabric eventFabric;

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
                        eventFabric().wireEmitter(bean, method);
                    }else{
                        // Blocking until registration completes makes startup ordering deterministic,
                        // the same trade ServiceRegistrationBeanPostProcessor makes for service registration
                        eventFabric().wireConsumer(bean, method)
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
        EventFabric fabric = eventFabric;
        return fabric != null && fabric.isWired(bean);
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        // Only reached for beans requiresDestruction accepted, so the fabric exists
        eventFabric.unwire(bean);
    }

    private EventFabric eventFabric() {
        EventFabric fabric = eventFabric;
        if(fabric == null){
            fabric = eventFabricProvider.getObject();
            eventFabric = fabric;
        }
        return fabric;
    }
}
