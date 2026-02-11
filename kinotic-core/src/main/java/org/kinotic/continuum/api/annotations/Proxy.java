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

package org.kinotic.continuum.api.annotations;

import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * {@link Proxy} annotations mark an interface as a proxy to a {@link Publish}ed service.
 * They will automatically be detected when the spring application boots if any exist in the {@link ContinuumPackages}.
 *
 * Created by Navid Mitchell on 2019-02-03.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed // TODO: do we need this ?
public @interface Proxy {

    /**
     * The logical namespace that can be used to locate the published service
     * If this is not provided the class package is used
     */
    String namespace() default "";

    /**
     * The logical name that can be used to locate the published service
     * If this is not provided the class name is used
     */
    String name() default "";

}
