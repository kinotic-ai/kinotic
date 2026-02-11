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

package org.kinotic.continuum.grind.internal.api;

import org.kinotic.continuum.grind.api.Task;
import org.springframework.context.support.GenericApplicationContext;

import java.util.function.Function;

/**
 * Generic {@link Task} that will "autowire" the instance prior to calling the provided invoker function
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class InstanceTask<T, R> extends AbstractTask<R> {

    private final T instance;
    private final Function<T, R> invokerFunction;

    public InstanceTask(T instance, Function<T, R> invokerFunction) {
        this.instance = instance;
        this.invokerFunction = invokerFunction;
    }

    public InstanceTask(String description,
                        T instance,
                        Function<T, R> invokerFunction) {
        super(description);
        this.instance = instance;
        this.invokerFunction = invokerFunction;
    }

    @Override
    public R execute(GenericApplicationContext applicationContext) throws Exception {
        applicationContext.getAutowireCapableBeanFactory().autowireBean(instance);
        applicationContext.getAutowireCapableBeanFactory().initializeBean(instance,"");
        return invokerFunction.apply(instance);
    }
}
