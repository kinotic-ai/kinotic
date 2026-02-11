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

package org.kinotic.continuum.core.api.service;

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * The {@link ServiceIdentifier} identifies a {@link ServiceDescriptor}
 * Created by Navíd Mitchell 🤪 on 8/18/21.
 */
public class ServiceIdentifier {

    private final String namespace;

    private final String name;

    // TODO: consider moving this somewhere else. It is not really appropriate for any {@link org.kinotic.continuum.api.annotations.Proxy} definitions
    private final String scope;

    private final String version;

    private final CRI cri;

    public ServiceIdentifier(String name, String version) {
        this(null, name, null, version);
    }

    public ServiceIdentifier(String namespace,
                             String name,
                             String scope,
                             String version) {
        Validate.notEmpty(name);
        Validate.notEmpty(version);
        this.namespace = namespace;
        this.name = name;
        this.scope = scope;
        this.version = version;

        cri = CRI.create(EventConstants.SERVICE_DESTINATION_SCHEME, this.scope, this.qualifiedName(),null, this.version);
    }

    /**
     * The namespace of this {@link ServiceIdentifier}
     * @return string containing the namespace or null if not provided
     */
    public String namespace() {
        return namespace;
    }

    /**
     * The name of this {@link ServiceIdentifier}
     * @return string containing the name
     */
    public String name() {
        return name;
    }


    /**
     * The scope of this {@link ServiceIdentifier}
     * The scope allows for multiple instances of the same service to be deployed to the cluster each having their own scope
     * A service can then be addressed by its scope
     * @return string containing the scope or null if not provided
     */
    public String scope() {
        return scope;
    }

    /**
     * The version for this service
     * @return string containing the version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the qualified name for this {@link ServiceIdentifier}
     * This is the namespace.name
     * @return string containing the qualified name
     */
    public String qualifiedName(){
        return (namespace != null && !namespace.isEmpty() ? namespace + "." : "") + name;
    }

    /**
     * The {@link CRI} that represents this {@link ServiceIdentifier}
     * @return the cri for this {@link ServiceIdentifier}
     */
    public CRI cri(){
        return cri;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof ServiceIdentifier)) return false;

        ServiceIdentifier that = (ServiceIdentifier) o;

        return new EqualsBuilder().append(namespace, that.namespace())
                                  .append(name, that.name())
                                  .append(scope, that.scope())
                                  .append(version, that.version())
                                  .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(namespace).append(name).append(scope).append(version).toHashCode();
    }

    @Override
    public String toString() {
        return this.cri.raw();
    }
}
