package org.kinotic.grindv2.internal;

import org.kinotic.grindv2.api.JobContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.core.env.Environment;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link JobContext} backed by a {@link DefaultListableBeanFactory} chained to its parent
 * scope's factory, so bean lookups traverse the scope hierarchy down to the application's own
 * beans and configuration.
 */
public class DefaultJobContext implements JobContext {

    private final DefaultJobContext parent;
    // Non-null only on the root scope, so property lookups end at the application configuration
    private final Environment fallbackEnvironment;
    private final DefaultListableBeanFactory beanFactory;
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    // Instances that received lifecycle initialization, so destroy() can invoke @PreDestroy on them
    private final List<Object> managedInstances = new CopyOnWriteArrayList<>();
    private final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", null, false);

    /**
     * Creates the root scope for a job execution.
     * @param applicationContext supplies the parent {@link BeanFactory} and fallback
     *                           {@link Environment}, or null for a standalone scope
     */
    public DefaultJobContext(ConfigurableApplicationContext applicationContext) {
        this(null,
             applicationContext != null ? applicationContext.getBeanFactory() : null,
             applicationContext != null ? applicationContext.getEnvironment() : null);
    }

    private DefaultJobContext(DefaultJobContext parent, BeanFactory parentBeanFactory, Environment fallbackEnvironment) {
        this.parent = parent;
        this.fallbackEnvironment = fallbackEnvironment;
        this.beanFactory = new DefaultListableBeanFactory(parentBeanFactory);

        // The subset of AnnotationConfigApplicationContext machinery injection actually needs:
        // @Autowired fields, @Value resolution, and @PostConstruct/@PreDestroy callbacks
        this.beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());

        AutowiredAnnotationBeanPostProcessor autowiredProcessor = new AutowiredAnnotationBeanPostProcessor();
        autowiredProcessor.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(autowiredProcessor);

        CommonAnnotationBeanPostProcessor lifecycleProcessor = new CommonAnnotationBeanPostProcessor();
        lifecycleProcessor.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(lifecycleProcessor);

        beanFactory.addEmbeddedValueResolver(this::resolvePlaceholders);
        beanFactory.registerResolvableDependency(JobContext.class, this);
    }

    @Override
    public <T> T getBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    @Override
    public <T> T getBeanOrNull(Class<T> type) {
        return beanFactory.getBeanProvider(type).getIfAvailable();
    }

    @Override
    public Object getProperty(String name) {
        Object ret = properties.get(name);
        if (ret == null && parent != null) {
            ret = parent.getProperty(name);
        }
        if (ret == null && fallbackEnvironment != null) {
            ret = fallbackEnvironment.getProperty(name);
        }
        return ret;
    }

    @Override
    public void autowire(Object instance) {
        beanFactory.autowireBean(instance);
        beanFactory.initializeBean(instance, instance.getClass().getSimpleName());
        managedInstances.add(instance);
    }

    @Override
    public <T> T instantiate(Class<T> type) {
        T ret = beanFactory.createBean(type);
        managedInstances.add(ret);
        return ret;
    }

    @Override
    public void storeProperty(String name, Object value) {
        properties.put(name, value);
    }

    @Override
    public void storeBean(String name, Object bean) {
        beanFactory.registerSingleton(name, bean);
    }

    /**
     * Creates a new scope with this one as its parent.
     * @return the new child scope
     */
    public DefaultJobContext createChild() {
        return new DefaultJobContext(this, beanFactory, null);
    }

    /**
     * Destroys this scope, invoking {@code @PreDestroy} on everything it manages. The scope
     * must not be used after this is called.
     */
    public void destroy() {
        for (Object instance : managedInstances) {
            beanFactory.destroyBean(instance);
        }
        managedInstances.clear();
        beanFactory.destroySingletons();
    }

    private String resolvePlaceholders(String value) {
        return placeholderHelper.replacePlaceholders(value, name -> Objects.toString(getProperty(name), null));
    }

}
