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
 * {@link Publish} denotes that an object should be treated as a public service accessible remotely.
 * This annotation is typically used on an interface and not on the implementing class.
 *
 * Created by Navid Mitchell on 2019-01-18.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed // TODO: do we need this ?
public @interface Publish {

    /**
     * The logical namespace that can be used to locate the published service
     * If this is not provided the class's package is used
     */
    String namespace() default "";

    /**
     * The logical name that can be used to locate the published service
     * If this is not provided the class name is used
     */
    String name() default "";

}
