/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.grind.api;

import org.apache.commons.io.IOUtils;
import org.kinotic.continuum.grind.internal.api.InstanceTask;
import org.kinotic.continuum.grind.internal.api.NoopTask;
import org.kinotic.continuum.grind.internal.api.ValueTask;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 3/24/20
 */
public class Tasks {

    public static <R> Task<R> fromCallable(Callable<R> instance) {
        return fromCallable(null, instance);
    }

    public static <R> Task<R> fromCallable(String description,
                                           Callable<R> instance) {
        return new InstanceTask<>(description,
                                  instance,
                                  callable -> {
                                      try {
                                          return callable.call();
                                      } catch (Exception e) {
                                          throw new RuntimeException(e);
                                      }
                                  });
    }

    public static Task<String> fromExec(String description,
                                        String... command) {
        return new Task<>() {
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String execute(GenericApplicationContext applicationContext) throws Exception {
                Process process = new ProcessBuilder(command).start();
                String out =  IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException(out);
                }
                return out;
            }
        };
    }

    public static <R> Task<R> fromSupplier(Supplier<R> instance) {
        return fromSupplier(null, instance);
    }

    public static <R> Task<R> fromSupplier(String description,
                                           Supplier<R> instance) {
        return new InstanceTask<>(description,
                                  instance,
                                  Supplier::get);
    }

    public static <T> Task<T> fromValue(T value) {
        return fromValue(null, value);
    }

    public static <T> Task<T> fromValue(String description,
                                        T value) {
        return new ValueTask<>(description, value);
    }

    public static Task<Void> fromRunnable(Runnable instance) {
        return fromRunnable(null, instance);
    }

    public static Task<Void> fromRunnable(String description,
                                          Runnable instance) {
        return new InstanceTask<>(description,
                                  instance,
                                  runnable -> {
                                      runnable.run();
                                      return null;
                                  });
    }

    /**
     * Special type of task that allows a step to be skipped if needed.
     * This is useful if a {@link Supplier<Task>} needs to only supply a task under certain conditions
     * @param description of why the task is a noop task.
     * @return the noop task
     */
    public static <T> Task<T> noop(String description){
        return new NoopTask<>(description);
    }

    /**
     * Special type of task that allows a step to be skipped if needed.
     * This is useful if a {@link Supplier<Task>} needs to only supply a task under certain conditions
     * @return the noop task
     */
    public static <T> Task<T> noop(){
        return new NoopTask<>();
    }


    public static <T, R> Task<R> transformResult(Task<T> from, Function<T, R> transformer){
        return new Task<>() {
            @Override
            public String getDescription() {
                return from.getDescription();
            }

            @Override
            public R execute(GenericApplicationContext applicationContext) throws Exception {
                return transformer.apply(from.execute(applicationContext));
            }
        };
    }

}
