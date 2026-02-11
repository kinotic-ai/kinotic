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

package org.kinotic.continuum.core.api.event;

/**
 * {@link CRI} is a Continuum Resource Identifier used by Continuum to route requests appropriately.
 *
 * The {@link CRI} is a URI the parts are just name differently for clarity as to purpose within Continuum
 *
 * Will be in a format as follows where anything surrounded with [] is optional
 *
 *      scheme://[scope@]resourceName[/path][#version]
 *
 * NOTE: If scope needs to be used to identify a sub-scope it will follow the form scope = scope:sub-scope
 *
 * This format can have varied meanings based upon the scheme used.
 *
 *
 * Created by Navid Mitchell on 4/30/20
 */
public interface CRI {

    /**
     * The scheme for this {@link CRI}
     *
     * @return a string containing the scheme
     */
    String scheme();

    /**
     * The scope for this {@link CRI} or null if not provided
     *
     * This is useful to narrow down the {@link CRI}
     * This could be something like a user id, device id, or a node id.
     *
     * @return a string containing the scope if provided or null if not set
     */
    String scope();

    /**
     * @return true if the {@link CRI#scope()} is set
     */
    boolean hasScope();

    /**
     * The name of the resource represented by this {@link CRI}
     *
     * In the case of a srv {@link CRI} this will be the service name.
     * In the case of a stream {@link CRI} this will be the name of the event type that the stream expects
     *
     * For the following CRI resourceName would be the portion specified by resourceName
     *
     * scheme://[scope@]resourceName/path
     *
     * @return the string containing the name of this resource
     */
    String resourceName();

    /**
     * This is a version for the resource or null if not provided
     *
     * @return a string containing the version if provided or null if not set
     */
    String version();

    /**
     * @return true if the {@link CRI#version()} is set
     */
    boolean hasVersion();

    /**
     * The path for this {@link CRI}
     *
     * For the following CRI path would be the portion specified by path
     *
     * scheme://[scope@]resourceName/path
     *
     * @return the path string if provided or null if not set
     */
    String path();

    /**
     * @return true if the {@link CRI#path()} is set
     */
    boolean hasPath();

    /**
     * Base Resource is a portion of the fully qualified {@link CRI} containing the following
     *
     * scheme://[scope@]resourceName
     *
     * @return string containing the baseResource
     */
    String baseResource();

    /**
     * The fully qualified value for this {@link CRI}
     *
     * @return the fully qualified {@link CRI} as a string
     */
    String raw();

    /**
     * Will create a new {@link CRI} from a raw string
     *
     * @param rawUrc the raw string
     * @return the newly created {@link CRI}
     */
    static CRI create(String rawUrc){
        return new DefaultCRI(rawUrc);
    }

    static CRI create(String scheme, String resourceName){
        return new DefaultCRI(scheme, null, resourceName, null, null);
    }

    /**
     * Will create a {@link CRI} from the values provided
     */
    static CRI create(String scheme, String scope, String resourceName){
        return new DefaultCRI(scheme, scope, resourceName, null, null);
    }

    /**
     * Will create a {@link CRI} from the values provided
     */
    static CRI create(String scheme, String scope, String resourceName, String path, String version){
        return new DefaultCRI(scheme, scope, resourceName, path, version);
    }

}
