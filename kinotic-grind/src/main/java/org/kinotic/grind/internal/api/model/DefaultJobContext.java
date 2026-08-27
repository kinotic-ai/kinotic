package org.kinotic.grind.internal.api.model;

import org.kinotic.grind.api.model.JobContext;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link JobContext} backed by a {@link DefaultListableBeanFactory} chained to its parent scope's factory,
 * so bean lookups traverse the scope hierarchy down to the application's own beans.
 */
public class DefaultJobContext implements JobContext {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final DefaultJobContext parent;
    // Non-null only on the root scope, so property lookups end at the application configuration
    private final Environment fallbackEnvironment;
    private final DefaultListableBeanFactory beanFactory;
    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    // Instances that received lifecycle initialization, so destroy() can invoke @PreDestroy on them
    private final List<Object> managedInstances = new CopyOnWriteArrayList<>();
    private final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", null, false);

    /**
     * Creates the root {@link JobContext} for a job execution.
     * @param applicationContext supplies the parent {@link BeanFactory} and fallback {@link Environment}
     */
    public DefaultJobContext(ConfigurableApplicationContext applicationContext) {
        this(null, applicationContext.getBeanFactory(), applicationContext.getEnvironment());
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
        if(ret == null && parent != null){
            ret = parent.getProperty(name);
        }
        if(ret == null && fallbackEnvironment != null){
            ret = fallbackEnvironment.getProperty(name);
        }
        return ret;
    }

    @Override
    public String resolvePlaceholders(String value) {
        return placeholderHelper.replacePlaceholders(value, name -> Objects.toString(getProperty(name), null));
    }

    @Override
    public <T> T evaluate(String spelExpression, Class<T> resultType) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setBeanResolver(new BeanFactoryResolver(beanFactory));

        // Root-first so a child scope's value wins over an ancestor's for the same name
        Deque<DefaultJobContext> chain = new ArrayDeque<>();
        for(DefaultJobContext ctx = this; ctx != null; ctx = ctx.parent){
            chain.addFirst(ctx);
        }
        for(DefaultJobContext ctx : chain){
            evalContext.setVariables(ctx.properties);
        }

        return SPEL_PARSER.parseExpression(spelExpression).getValue(evalContext, resultType);
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

    @Override
    public JobContext createChild() {
        return new DefaultJobContext(this, beanFactory, null);
    }

    @Override
    public void destroy() {
        for(Object instance : managedInstances){
            beanFactory.destroyBean(instance);
        }
        managedInstances.clear();
        beanFactory.destroySingletons();
    }

}
