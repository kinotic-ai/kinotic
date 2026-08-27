package org.kinotic.grindv2.internal;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.grindv2.api.JobContext;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobScope;
import org.kinotic.grindv2.api.Step;
import org.kinotic.grindv2.api.StoreType;
import org.kinotic.grindv2.api.Task;

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
 * Compiles a steps class into a {@link DefaultJobDefinition}: one step per {@link Step}
 * method in {@link Step#order()}, parameters injected from the job scope by type, return
 * values stored back under the method's declared {@link StoreType}. The class is instantiated
 * once per run through the job scope, so its constructor arguments resolve against the
 * application context without the class being a Spring bean.
 */
public class StepsClassCompiler {

    /**
     * Compiles the given steps class.
     * @param stepsClass the class to compile
     * @return the definition
     * @throws IllegalArgumentException if the class declares no steps, duplicate orders, a
     *         step consuming a type only a later step produces, or durable state on a step
     *         that produces no value
     */
    public static JobDefinition compile(Class<?> stepsClass) {
        List<Method> methods = stepMethodsInOrder(stepsClass);
        validateWiring(stepsClass, methods);

        DefaultJobDefinition ret = new DefaultJobDefinition(stepsClass.getSimpleName(), JobScope.CHILD, false);
        for (Method method : methods) {
            Step meta = method.getAnnotation(Step.class);
            String description = meta.value().isEmpty() ? method.getName() : meta.value();
            Class<?> produced = producedType(method);
            StoreType store = effectiveStoreType(stepsClass, method, meta, produced);
            Task<Object> task = stepTask(stepsClass, method, description);
            switch (store) {
                case NONE -> ret.task(task);
                case RESULT -> ret.taskStoreResult(task, Introspector.decapitalize(produced.getSimpleName()));
                case STATE -> ret.taskStoreState(task, Introspector.decapitalize(produced.getSimpleName()));
            }
        }
        return ret;
    }

    private static List<Method> stepMethodsInOrder(Class<?> stepsClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : stepsClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Step.class)) {
                methods.add(method);
            }
        }
        Validate.isTrue(!methods.isEmpty(), "%s declares no @Step methods", stepsClass.getName());
        methods.sort(Comparator.comparingInt(method -> method.getAnnotation(Step.class).order()));
        Set<Integer> orders = new HashSet<>();
        for (Method method : methods) {
            int order = method.getAnnotation(Step.class).order();
            Validate.isTrue(orders.add(order), "%s declares @Step order %s more than once",
                            stepsClass.getName(), order);
        }
        return methods;
    }

    /**
     * Fails fast when a step consumes a type that only a later step produces - the one wiring
     * mistake the sequence itself can reveal. Types no step produces are assumed to come from
     * the job's inputs or the application context and resolve at execution time.
     */
    private static void validateWiring(Class<?> stepsClass, List<Method> methods) {
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
                                "%s.%s consumes a %s that only a later step produces",
                                stepsClass.getSimpleName(), methods.get(i).getName(),
                                parameter.getType().getSimpleName());
            }
        }
    }

    /**
     * The type a step method contributes to the job scope: its return type with
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

    private static StoreType effectiveStoreType(Class<?> stepsClass, Method method, Step meta, Class<?> produced) {
        StoreType ret;
        if (produced != null) {
            ret = meta.store();
        } else {
            // nothing storable is produced: the RESULT default quietly becomes NONE, but an
            // explicit STATE declaration is a contract the method cannot honor
            Validate.isTrue(meta.store() != StoreType.STATE,
                            "%s.%s declares STATE but produces no storable value",
                            stepsClass.getSimpleName(), method.getName());
            ret = StoreType.NONE;
        }
        return ret;
    }

    private static Task<Object> stepTask(Class<?> stepsClass, Method method, String description) {
        method.trySetAccessible();
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Object execute(JobContext context) throws Exception {
                Object instance = stepsInstance(context, stepsClass);
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
     * The steps class is instantiated once per run: the first step to need it constructs it
     * through the scope, with constructor arguments resolved against the application context,
     * and stores it for the later steps.
     */
    private static Object stepsInstance(JobContext context, Class<?> stepsClass) {
        Object ret = context.getBeanOrNull(stepsClass);
        if (ret == null) {
            ret = context.instantiate(stepsClass);
            context.storeBean(stepsClass.getName(), ret);
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
                throw new IllegalStateException("Step '" + description + "' requires a " + type.getSimpleName()
                        + " but nothing in the job scope provides one");
            }
            ret[i] = value;
        }
        return ret;
    }

}
