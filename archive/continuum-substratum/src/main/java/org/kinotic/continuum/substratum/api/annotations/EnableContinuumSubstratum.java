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

package org.kinotic.continuum.substratum.api.annotations;

import org.kinotic.continuum.grind.api.annotations.EnableContinuumGrind;
import org.kinotic.continuum.substratum.internal.config.ContinuumSubstratumConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used on a Spring Boot application to enable the Continuum Substratum.
 *
 * Continuum Substratum provides Grind tasks to provision the Continuum Cloud
 *
 *
 * Created by Navid Mitchell 🤪 on 2/10/20
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ContinuumSubstratumConfiguration.class)
@EnableContinuumGrind
public @interface EnableContinuumSubstratum {

}
