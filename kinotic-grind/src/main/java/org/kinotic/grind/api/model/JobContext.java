package org.kinotic.grind.api.model;

/**
 * The execution scope for {@link Task}'s within a {@link JobDefinition}.
 *
 * Results stored by {@link JobDefinition#taskStoreResult(Task)} are available to subsequent {@link Task}'s
 * in the same scope and any child scope. Beans are injected into {@link Task} instances with
 * {@code @Autowired}, named values with {@code @Value("${name}")}. All lookups check this scope first
 * and then each parent scope in turn, ending at the application's own beans and configuration.
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
     * Replaces all {@code ${name}} placeholders in the given string with the corresponding stored values.
     * @param value the string containing placeholders to resolve
     * @return the resolved string
     * @throws IllegalArgumentException if a placeholder cannot be resolved
     */
    String resolvePlaceholders(String value);

    /**
     * Evaluates a SpEL expression against this {@link JobContext}.
     * Stored values are available as variables, {@code #name}, and stored beans as references, {@code @name}.
     * @param spelExpression the expression to evaluate
     * @param resultType the type the expression result will be coerced to
     * @param <T> the expected result type
     * @return the expression result
     */
    <T> T evaluate(String spelExpression, Class<T> resultType);

    /**
     * Injects the given instance's {@code @Autowired} and {@code @Value} members and invokes
     * any {@code @PostConstruct} methods. {@code @PreDestroy} methods are invoked when this
     * {@link JobContext} is destroyed.
     * @param instance to inject
     */
    void autowire(Object instance);

    /**
     * Constructs a new instance of the given type with full injection,
     * equivalent to {@link #autowire(Object)} applied to a new instance.
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
     * Stores a bean for the given name, making it available for {@code @Autowired} injection.
     * @param name to store the bean under
     * @param bean to store
     */
    void storeBean(String name, Object bean);

    /**
     * Creates a new {@link JobContext} with this one as its parent.
     * @return the new child {@link JobContext}
     */
    JobContext createChild();

    /**
     * Destroys this {@link JobContext}, invoking {@code @PreDestroy} on everything it manages.
     * The scope must not be used after this is called.
     */
    void destroy();

}
