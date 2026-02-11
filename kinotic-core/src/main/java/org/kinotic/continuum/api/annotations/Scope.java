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

import java.lang.annotation.*;

/**
 * {@link Scope} denotes that a service will have more than one instance available.
 * The {@link Scope} can then be used to resolve a particular instance of a service.
 *
 * If {@link Scope} is used on a service that is published via {@link Publish} then the {@link Scope} identifies that service instance.
 * If {@link Scope} is used on a method parameter of a {@link Proxy} the {@link Scope} is used to determine which service instance rpc requests should be routed to.
 *
 *
 * Created by Navid Mitchell on 2019-01-18.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {
}
