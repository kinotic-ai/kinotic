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
 * An interesting type of {@link Task} that lets you define a class to be constructed then a method invoked for the result
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class ClassTask<T, R> extends AbstractTask<R> {

    private final Class<? extends T> clazz;
    private final Function<T, R> invokerFunction;

    public ClassTask(Class<? extends T> clazz,
                     Function<T, R> invokerFunction) {
        this.clazz = clazz;
        this.invokerFunction = invokerFunction;
    }

    public ClassTask(String description,
                     Class<? extends T> clazz,
                     Function<T, R> invokerFunction) {
        super(description);
        this.clazz = clazz;
        this.invokerFunction = invokerFunction;
    }

    @Override
    public R execute(GenericApplicationContext applicationContext) {
        T bean = applicationContext.getAutowireCapableBeanFactory().createBean(clazz);
        return invokerFunction.apply(bean);
    }

}
