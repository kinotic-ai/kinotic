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

import { CRI } from "./CRI"

/**
 * Default implementation of the `CRI` interface.
 *
 * @author Navid Mitchell
 * @since 3/25/25
 */
export class DefaultCRI implements CRI {
    private readonly _scheme: string
    private readonly _scope: string | null
    private readonly _resourceName: string
    private readonly _path: string | null
    private readonly _version: string | null
    private readonly _raw: string

    constructor(rawCRI: string)
    constructor(scheme: string, scope: string | null, resourceName: string, path: string | null, version: string | null)
    constructor(...args: any[]) {
        if (args.length === 1) {
            const rawURC = args[0]
            if (typeof rawURC !== "string") {
                throw new Error("Raw URI must be a string")
            }
            const parsed = DefaultCRI.parseRaw(rawURC)
            this._scheme = parsed.scheme
            this._scope = parsed.scope
            this._resourceName = parsed.resourceName
            this._path = parsed.path
            this._version = parsed.version
            this._raw = rawURC
        } else if (args.length === 5) {
            const [scheme, scope, resourceName, path, version] = args
            this._scheme = scheme
            this._scope = scope
            this._resourceName = resourceName
            this._path = path
            this._version = version
            this._raw = DefaultCRI.buildRaw(scheme, scope, resourceName, path, version)
        } else {
            throw new Error("Invalid constructor arguments for DefaultCRI")
        }

        if (!this._scheme || !this._resourceName) {
            throw new Error(`Invalid CRI: scheme and resourceName are required. Got: ${this._raw}`)
        }
    }

    public scheme(): string {
        return this._scheme
    }

    public scope(): string | null {
        return this._scope
    }

    public hasScope(): boolean {
        return this._scope !== null
    }

    public resourceName(): string {
        return this._resourceName
    }

    public version(): string | null {
        return this._version
    }

    public hasVersion(): boolean {
        return this._version !== null
    }

    public path(): string | null {
        return this._path
    }

    public hasPath(): boolean {
        return this._path !== null
    }

    public baseResource(): string {
        let result = `${this._scheme}://`
        if (this.hasScope()) {
            result += `${this._scope}@`
        }
        result += this._resourceName
        return result
    }

    public raw(): string {
        return this._raw
    }

    public equals(other: any): boolean {
        if (this === other) return true
        if (!(other instanceof DefaultCRI)) return false
        return this._raw === other.raw()
    }

    public hashCode(): number {
        let hash = 17
        hash = hash * 37 + this._raw.split("").reduce((a, c) => a + c.charCodeAt(0), 0)
        return hash
    }

    public toString(): string {
        return this._raw
    }

    // Helper to parse a raw CRI string
    private static parseRaw(raw: string): {
        scheme: string
        scope: string | null
        resourceName: string
        path: string | null
        version: string | null
    } {
        const regex = /^([^:]+):\/\/(?:([^@]+)@)?([^\/#]+)(\/[^#]*)?(?:#(.+))?$/;
        const match = raw.match(regex)
        if (!match) {
            throw new Error(`Invalid CRI format: ${raw}`)
        }

        const [, scheme, scope, resourceName, path, version] = match
        return {
            scheme,
            scope: scope || null,
            resourceName,
            path: path ? path.substring(1) : null,
            version: version || null,
        }
    }

    // Helper to build a raw CRI string
    private static buildRaw(
        scheme: string,
        scope: string | null,
        resourceName: string,
        path: string | null,
        version: string | null
    ): string {
        let result = `${scheme}://`
        if (scope) {
            result += `${scope}@`
        }
        result += resourceName
        if (path) {
            result += `/${path}`
        }
        if (version) {
            result += `#${version}`
        }
        return result
    }
}
