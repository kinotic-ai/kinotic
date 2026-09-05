package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobScope;

import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Task;

import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Compiles a tasks class into a {@link DefaultJobDefinition}: one task per {@link org.kinotic.grind.api.annotations.Task}
 * method in {@link org.kinotic.grind.api.annotations.Task#order()}, parameters injected from the job scope by type, return
 * values stored back under the method's declared {@link StoreType}. The class is instantiated
 * once per run through the job scope, so its constructor arguments resolve against the
 * application context without the class being a Spring bean.
 */
public class TaskClassCompiler {

    /**
     * Compiles the given tasks class.
     * @param taskClass the class to compile
     * @return the definition
     * @throws IllegalArgumentException if the class declares no tasks, duplicate orders, a
     *         task consuming a type only a later task produces, or durable state on a task
     *         that produces no value
     */
    public static JobDefinition compile(Class<?> taskClass) {
        List<Method> methods = taskMethodsInOrder(taskClass);
        validateWiring(taskClass, methods);

        DefaultJobDefinition ret = new DefaultJobDefinition(taskClass.getSimpleName(), JobScope.CHILD, false);
        for (Method method : methods) {
            org.kinotic.grind.api.annotations.Task meta = method.getAnnotation(org.kinotic.grind.api.annotations.Task.class);
            String description = meta.value().isEmpty() ? method.getName() : meta.value();
            Class<?> produced = producedType(method);
            Task<Object> task = methodTask(taskClass, method, description);
            ret.task(task, effectiveStore(taskClass, method, meta, produced));
        }
        return ret;
    }

    private static List<Method> taskMethodsInOrder(Class<?> taskClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : taskClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.kinotic.grind.api.annotations.Task.class)) {
                methods.add(method);
            }
        }
        Validate.isTrue(!methods.isEmpty(), "%s declares no @Task methods", taskClass.getName());
        methods.sort(Comparator.comparingInt(method -> method.getAnnotation(org.kinotic.grind.api.annotations.Task.class).order()));
        Set<Integer> orders = new HashSet<>();
        for (Method method : methods) {
            int order = method.getAnnotation(org.kinotic.grind.api.annotations.Task.class).order();
            Validate.isTrue(orders.add(order), "%s declares @Task order %s more than once",
                            taskClass.getName(), order);
        }
        return methods;
    }

    /**
     * Fails fast when a task consumes a type that only a later task produces - the one wiring
     * mistake the sequence itself can reveal. Types no task produces are assumed to come from
     * the job's inputs or the application context and resolve at execution time.
     */
    private static void validateWiring(Class<?> taskClass, List<Method> methods) {
        Map<Class<?>, Integer> firstProducerIndex = new HashMap<>();
        for (int i = 0; i < methods.size(); i++) {
            Class<?> produced = producedType(methods.get(i));
            if (produced != null) {
                firstProducerIndex.putIfAbsent(produced, i);
            }
        }
        for (int i = 0; i < methods.size(); i++) {
            for (Parameter parameter : methods.get(i).getParameters()) {
                Integer producerIndex = firstProducerIndex.get(parameter.getType());
                Validate.isTrue(producerIndex == null || producerIndex < i,
                                "%s.%s consumes a %s that only a later task produces",
                                taskClass.getSimpleName(), methods.get(i).getName(),
                                parameter.getType().getSimpleName());
            }
        }
    }

    /**
     * The type a task method contributes to the job scope: its return type with
     * {@code Future}/{@code CompletionStage} unwrapped, or null for methods producing nothing
     * storable - void, or dynamic structure such as a returned {@link JobDefinition} or
     * {@link Task}.
     */
    private static Class<?> producedType(Method method) {
        Class<?> ret = method.getReturnType();
        if (Future.class.isAssignableFrom(ret) || CompletionStage.class.isAssignableFrom(ret)) {
            ret = genericArgument(method.getGenericReturnType());
        }
        if (ret == void.class || ret == Void.class
                || (ret != null && JobDefinition.class.isAssignableFrom(ret))
                || (ret != null && Task.class.isAssignableFrom(ret))) {
            ret = null;
        }
        return ret;
    }

    private static Class<?> genericArgument(Type genericReturnType) {
        Class<?> ret = null;
        if (genericReturnType instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> argument) {
            ret = argument;
        }
        return ret;
    }

    private static Store effectiveStore(Class<?> taskClass, Method method, org.kinotic.grind.api.annotations.Task meta, Class<?> produced) {
        Store ret;
        if (produced != null) {
            String name = Introspector.decapitalize(produced.getSimpleName());
            ret = switch (meta.store()) {
                case NONE -> Store.none();
                case RESULT -> Store.result(name);
                case STATE -> Store.state(name);
            };
            if (meta.wire()) {
                ret = ret.wire();
            }
        } else {
            // nothing storable is produced: the RESULT default quietly becomes NONE, but an
            // explicit STATE or wire declaration is a contract the method cannot honor
            Validate.isTrue(meta.store() != StoreType.STATE,
                            "%s.%s declares STATE but produces no storable value",
                            taskClass.getSimpleName(), method.getName());
            Validate.isTrue(!meta.wire(),
                            "%s.%s declares wire but produces no storable value",
                            taskClass.getSimpleName(), method.getName());
            ret = Store.none();
        }
        return ret;
    }

    private static Task<Object> methodTask(Class<?> taskClass, Method method, String description) {
        method.trySetAccessible();
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Object execute(JobContext context) throws Exception {
                Object instance = tasksInstance(context, taskClass);
                Object[] arguments = resolveArguments(context, method, description);
                try {
                    return method.invoke(instance, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause() instanceof Exception cause ? cause : e;
                }
            }
        };
    }

    /**
     * The tasks class is instantiated once per run: the first task to need it constructs it
     * through the scope, with constructor arguments resolved against the application context,
     * and stores it for the later tasks.
     */
    private static Object tasksInstance(JobContext context, Class<?> taskClass) {
        Object ret = context.getBeanOrNull(taskClass);
        if (ret == null) {
            ret = context.instantiate(taskClass);
            context.storeBean(taskClass.getName(), ret);
        }
        return ret;
    }

    private static Object[] resolveArguments(JobContext context, Method method, String description) {
        Parameter[] parameters = method.getParameters();
        Object[] ret = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> type = parameters[i].getType();
            Object value = context.getBeanOrNull(type);
            if (value == null) {
                Object property = context.getProperty(Introspector.decapitalize(type.getSimpleName()));
                value = type.isInstance(property) ? property : null;
            }
            if (value == null) {
                throw new IllegalStateException("Task '" + description + "' requires a " + type.getSimpleName()
                        + " but nothing in the job scope provides one");
            }
            ret[i] = value;
        }
        return ret;
    }

}
