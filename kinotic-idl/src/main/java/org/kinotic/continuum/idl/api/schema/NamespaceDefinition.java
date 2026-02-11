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

package org.kinotic.continuum.idl.api.schema;

import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides functionality to define a namespace with a Continuum schema.
 * A {@link NamespaceDefinition} is a collection of {@link ServiceDefinition}'s and {@link ObjectC3Type}'s defined within a particular namespace.
 * <p>
 * Created by navid on 2023-4-13.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class NamespaceDefinition {

    /**
     * This is the name of this {@link NamespaceDefinition}
     */
    private String name;

    /**
     * This is all the objects defined for a given namespace
     */
    private Set<ComplexC3Type> complexC3Types = new HashSet<>();

    /**
     * This is all the services defined for a given namespace
     */
    private Set<ServiceDefinition> services = new HashSet<>();


    public NamespaceDefinition addComplexC3Type(ComplexC3Type type){
        Validate.isTrue(!complexC3Types.contains(type), "This NamespaceDefinition already contains an ComplexC3Type" + type);
        complexC3Types.add(type);
        return this;
    }

    public NamespaceDefinition addServiceDefinition(ServiceDefinition service){
        Validate.isTrue(!services.contains(service), "This NamespaceDefinition already contains a ServiceDefinition "+service);
        services.add(service);
        return this;
    }

}
