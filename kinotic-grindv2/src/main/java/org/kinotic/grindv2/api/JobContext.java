package org.kinotic.grindv2.api;

/**
 * The scope shared by the steps of a running job. Values stored by earlier steps are
 * available to later steps: beans by type through {@code @Autowired} injection or
 * {@link #getBean(Class)}, named values through {@code @Value("${name}")} or
 * {@link #getProperty(String)}. Lookups fall through parent scopes down to the
 * application's own beans and configuration.
 */
public interface JobContext {

    /**
     * Returns the bean matching the given type.
     * @param type of the bean to find
     * @param <T> the expected bean type
     * @return the bean
     * @throws org.springframework.beans.BeansException if no bean or more than one bean matches
     */
    <T> T getBean(Class<T> type);

    /**
     * Returns the bean matching the given type or null if none exists.
     * @param type of the bean to find
     * @param <T> the expected bean type
     * @return the bean or null if no bean matches
     */
    <T> T getBeanOrNull(Class<T> type);

    /**
     * Returns the value stored for the given name.
     * @param name of the value to find
     * @return the value or null if no value is stored for the given name
     */
    Object getProperty(String name);

    /**
     * Injects the given instance's {@code @Autowired} and {@code @Value} members and invokes
     * any {@code @PostConstruct} methods. {@code @PreDestroy} methods are invoked when this
     * scope is destroyed.
     * @param instance to inject
     */
    void autowire(Object instance);

    /**
     * Constructs a new instance of the given type with full injection, equivalent to
     * {@link #autowire(Object)} applied to a new instance.
     * @param type to construct
     * @param <T> the type to construct
     * @return the new instance
     */
    <T> T instantiate(Class<T> type);

    /**
     * Stores a value for the given name, replacing any value this scope already holds for it.
     * @param name to store the value under
     * @param value to store
     */
    void storeProperty(String name, Object value);

    /**
     * Stores a bean for the given name, making it available for {@code @Autowired} injection
     * and {@link #getBean(Class)} lookup.
     * @param name to store the bean under
     * @param bean to store
     */
    void storeBean(String name, Object bean);

}
