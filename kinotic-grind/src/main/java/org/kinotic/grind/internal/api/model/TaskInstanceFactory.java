package org.kinotic.grind.internal.api.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.JobContext;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Supplies the instance each execution of a task invokes, injected against the job scope. An
 * instance the scope injects, through a field, a method or a lifecycle callback, holds the values
 * of one execution, so every execution is given an instance of its own: a new instance of the
 * given one's anonymous class, constructed with the values the given one captured. Any other
 * instance serves every execution.
 *
 * @param <T> the type of the instance
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskInstanceFactory<T> {

    private static final SpringObjenesis OBJENESIS = new SpringObjenesis();
    private static final String NOT_REPEATABLE = "%s cannot be constructed again from what it captured: "
            + "give its class a name and give the class to Tasks.fromClass";

    private final T template;
    // Constructs the instance of one execution, or null when the template serves every execution
    private final Constructor<?> constructor;
    private final Object[] arguments;

    /**
     * Creates the factory for the given instance.
     * @param template the instance a task was built from
     * @param <T> the type of the instance
     * @return the factory
     * @throws IllegalArgumentException if the scope injects the instance and a new instance of its
     *         class cannot be constructed from it
     */
    public static <T> TaskInstanceFactory<T> of(T template) {
        Class<?> type = template.getClass();
        TaskInstanceFactory<T> ret;
        if (DefaultJobContext.injects(type)) {
            Validate.isTrue(type.isAnonymousClass(),
                            "An instance of %s holds what one execution injects, so each execution needs an "
                                    + "instance of its own: give the class to Tasks.fromClass", type.getName());
            // an anonymous subclass passes its constructor arguments on to its superclass and keeps no
            // field for them, so they cannot be read back
            Validate.isTrue(type.getSuperclass() == Object.class, NOT_REPEATABLE, type.getName());
            Constructor<?> constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            ret = new TaskInstanceFactory<>(template, constructor, capturedArguments(template, constructor));
            // the mapping of captured values to constructor parameters follows the compiler's layout,
            // so it is accepted only once a constructed instance is seen to hold what the template holds
            ret.requireSameCaptures(ret.construct());
        } else {
            ret = new TaskInstanceFactory<>(template, null, null);
        }
        return ret;
    }

    /**
     * @param context the scope of the execution
     * @return the instance the execution invokes, injected against the given scope
     */
    public T instanceFor(JobContext context) {
        T ret = constructor != null ? construct() : template;
        context.autowire(ret);
        return ret;
    }

    // An anonymous class's constructor takes its enclosing instance, when it is declared in one, then
    // each local variable its body captured, in the order the compiler declared the fields holding them
    private static Object[] capturedArguments(Object template, Constructor<?> constructor) {
        Field enclosing = null;
        List<Field> captured = new ArrayList<>();
        for (Field field : syntheticFields(template.getClass())) {
            if (field.getName().startsWith("this$")) {
                enclosing = field;
            } else {
                captured.add(field);
            }
        }
        Class<?>[] parameters = constructor.getParameterTypes();
        boolean takesEnclosing = parameters.length == captured.size() + 1
                && parameters[0] == template.getClass().getEnclosingClass();
        Validate.isTrue(takesEnclosing || parameters.length == captured.size(), NOT_REPEATABLE, template.getClass().getName());
        List<Object> ret = new ArrayList<>();
        if (takesEnclosing) {
            // a body that never uses its enclosing instance keeps no field for it, and its constructor
            // only checks the argument is not null
            ret.add(enclosing != null ? ReflectionUtils.getField(enclosing, template) : standIn(template.getClass()));
        }
        for (Field field : captured) {
            Validate.isTrue(field.getType() == parameters[ret.size()], NOT_REPEATABLE, template.getClass().getName());
            ret.add(ReflectionUtils.getField(field, template));
        }
        return ret.toArray();
    }

    // An instance of the given anonymous class's enclosing class that no constructor ran for, of a
    // generated subclass when the enclosing class is abstract or an interface
    private static Object standIn(Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        try {
            if (Modifier.isAbstract(enclosing.getModifiers())) {
                Enhancer enhancer = new Enhancer();
                enhancer.setSuperclass(enclosing);
                enhancer.setCallbackType(NoOp.class);
                enhancer.setUseFactory(false);
                enclosing = enhancer.createClass();
            }
            return OBJENESIS.newInstance(enclosing);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(String.format(NOT_REPEATABLE, type.getName()), e);
        }
    }

    private static List<Field> syntheticFields(Class<?> type) {
        List<Field> ret = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                field.setAccessible(true);
                ret.add(field);
            }
        }
        return ret;
    }

    private void requireSameCaptures(Object constructed) {
        for (Field field : syntheticFields(template.getClass())) {
            Object expected = ReflectionUtils.getField(field, template);
            Object actual = ReflectionUtils.getField(field, constructed);
            Validate.isTrue(field.getType().isPrimitive() ? Objects.equals(expected, actual) : expected == actual,
                            NOT_REPEATABLE, template.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private T construct() {
        try {
            return (T) constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not construct " + template.getClass().getName(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct " + template.getClass().getName(), e);
        }
    }

}
