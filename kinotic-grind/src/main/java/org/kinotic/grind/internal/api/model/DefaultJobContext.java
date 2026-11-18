package org.kinotic.grind.internal.api.model;

import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.TaskReporter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
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

    // The processors every scope injects with, held here only to report the members they act on
    private static final List<MergedBeanDefinitionPostProcessor> MEMBER_FINDERS =
            List.of(new AutowiredAnnotationBeanPostProcessor(), new CommonAnnotationBeanPostProcessor());

    private final DefaultJobContext parent;
    // The scope stored values and managed instances belong to: this one, or for a task scope the
    // scope the task executes in, which outlives the task
    private final DefaultJobContext owner;
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
        this(parent, parentBeanFactory, fallbackEnvironment, null);
    }

    private DefaultJobContext(DefaultJobContext parent, BeanFactory parentBeanFactory, Environment fallbackEnvironment,
                              DefaultJobContext owner) {
        this.parent = parent;
        this.owner = owner != null ? owner : this;
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

    /**
     * @param type the class of an instance
     * @return true if {@link #autowire(Object)} injects a field or a method of an instance of the
     *         given type, or invokes a lifecycle callback on it
     */
    public static boolean injects(Class<?> type) {
        RootBeanDefinition definition = new RootBeanDefinition(type);
        for (MergedBeanDefinitionPostProcessor finder : MEMBER_FINDERS) {
            finder.postProcessMergedBeanDefinition(definition, type, type.getName());
        }
        return !definition.getExternallyManagedConfigMembers().isEmpty()
                || !definition.getExternallyManagedInitMethods().isEmpty()
                || !definition.getExternallyManagedDestroyMethods().isEmpty();
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
        owner.managedInstances.add(instance);
    }

    @Override
    public <T> T instantiate(Class<T> type) {
        T ret = beanFactory.createBean(type);
        owner.managedInstances.add(ret);
        return ret;
    }

    @Override
    public void storeProperty(String name, Object value) {
        owner.properties.put(name, value);
    }

    @Override
    public void storeBean(String name, Object bean) {
        owner.beanFactory.registerSingleton(name, bean);
    }

    /**
     * Creates a new scope with this one as its parent.
     * @return the new child scope
     */
    public DefaultJobContext createChild() {
        return new DefaultJobContext(this, beanFactory, null);
    }

    /**
     * @return the scope the values this scope stores belong to: this scope, or for a task's scope
     *         the scope the task executes in, shared with the tasks beside it
     */
    public DefaultJobContext sharedScope() {
        return owner;
    }

    /**
     * Creates the scope one task executes in: its lookups find the given {@link TaskReporter} first,
     * then everything this scope finds, and what the task stores, instantiates or autowires belongs
     * to this scope.
     * @param reporter the reporter given to the task
     * @return the task's scope
     */
    public DefaultJobContext createTaskScope(TaskReporter reporter) {
        DefaultJobContext ret = new DefaultJobContext(this, beanFactory, null, this);
        ret.beanFactory.registerSingleton(TaskReporter.class.getName(), reporter);
        return ret;
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
