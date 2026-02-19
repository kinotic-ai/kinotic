/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import {DefaultCRI} from './DefaultCRI.js'

/**
 * `CRI` is a Continuum Resource Identifier used by Continuum to route requests appropriately.
 *
 * The `CRI` is a URI where the parts are named differently for clarity as to their purpose within Continuum.
 *
 * Will be in a format as follows where anything surrounded with `[]` is optional:
 *
 *      scheme://[scope@]resourceName[/path][#version]
 *
 * NOTE: If scope needs to be used to identify a sub-scope, it will follow the form `scope = scope:sub-scope`.
 *
 * This format can have varied meanings based upon the scheme used.
 *
 * @author Navid Mitchell
 * @since 3/25/25
 */
export interface CRI {
    /**
     * The scheme for this `CRI`.
     *
     * @returns a string containing the scheme
     */
    scheme(): string

    /**
     * The scope for this `CRI` or `null` if not provided.
     *
     * This is useful to narrow down the `CRI`. This could be something like a user id, device id, or a node id.
     *
     * @returns a string containing the scope if provided or `null` if not set
     */
    scope(): string | null

    /**
     * @returns `true` if the `scope` is set
     */
    hasScope(): boolean

    /**
     * The name of the resource represented by this `CRI`.
     *
     * In the case of a `srv` `CRI`, this will be the service name.
     * In the case of a `stream` `CRI`, this will be the name of the event type that the stream expects.
     *
     * For the following CRI, `resourceName` would be the portion specified by `resourceName`:
     *
     * `scheme://[scope@]resourceName/path`
     *
     * @returns the string containing the name of this resource
     */
    resourceName(): string

    /**
     * This is a version for the resource or `null` if not provided.
     *
     * @returns a string containing the version if provided or `null` if not set
     */
    version(): string | null

    /**
     * @returns `true` if the `version` is set
     */
    hasVersion(): boolean

    /**
     * The path for this `CRI`, without a leading `/`.
     *
     * For the following CRI, `path` would be the portion specified by `path`:
     *
     * `scheme://[scope@]resourceName/path`
     *
     * @returns the path string if provided or `null` if not set
     */
    path(): string | null

    /**
     * @returns `true` if the `path` is set
     */
    hasPath(): boolean

    /**
     * Base Resource is a portion of the fully qualified `CRI` containing the following:
     *
     * `scheme://[scope@]resourceName`
     *
     * @returns string containing the baseResource
     */
    baseResource(): string

    /**
     * The fully qualified value for this `CRI`.
     *
     * @returns the fully qualified `CRI` as a string
     */
    raw(): string
}

/**
 * Creates a new `CRI` from a raw string.
 *
 * @param rawUrc the raw string
 * @returns the newly created `CRI`
 */
export function createCRI(rawUrc: string): CRI;

/**
 * Creates a new `CRI` from scheme and resourceName.
 *
 * @param scheme the scheme
 * @param resourceName the resource name
 * @returns the newly created `CRI`
 */
export function createCRI(scheme: string, resourceName: string): CRI;
/**
 * Creates a new `CRI` from scheme, scope, and resourceName.
 *
 * @param scheme the scheme
 * @param scope the scope
 * @param resourceName the resource name
 * @returns the newly created `CRI`
 */
export function createCRI(scheme: string, scope: string | null, resourceName: string): CRI;
/**
 * Creates a new `CRI` from all provided values.
 *
 * @param scheme the scheme
 * @param scope the scope
 * @param resourceName the resource name
 * @param path the path
 * @param version the version
 * @returns the newly created `CRI`
 */
export function createCRI(scheme: string, scope: string | null, resourceName: string, path: string | null, version: string | null): CRI;

// Implementation of the overloaded createCRI function
export function createCRI(...args: any[]): CRI {
    if (args.length === 1) return new DefaultCRI(args[0]);
    if (args.length === 2) return new DefaultCRI(args[0], null, args[1], null, null);
    if (args.length === 3) return new DefaultCRI(args[0], args[1], args[2], null, null);
    if (args.length === 5) return new DefaultCRI(args[0], args[1], args[2], args[3], args[4]);
    throw new Error("Invalid arguments for createCRI");
}
