import type {CRI} from "./CRI"

const ZONE_DELIMITER = '~'

/**
 * Default implementation of the `CRI` interface.
 *
 * @author Navid Mitchell
 * @since 3/25/25
 */
export class DefaultCRI implements CRI {
    private readonly _scheme: string
    private readonly _scope: string | null
    private readonly _zone: string | null
    private readonly _resourceName: string
    private readonly _path: string | null
    private readonly _version: string | null
    private readonly _raw: string

    constructor(rawCRI: string)
    constructor(scheme: string, scope: string | null, resourceName: string, path: string | null, version: string | null)
    constructor(...args: any[]) {
        // The scheme, scope, zone, and resourceName are the CRI's identity, so they are
        // lowercased; the path and version are payload details and keep their case.
        if (args.length === 1) {
            const rawURC = args[0]
            if (typeof rawURC !== "string") {
                throw new Error("Raw URI must be a string")
            }
            const parsed = DefaultCRI.parseRaw(rawURC)
            this._scheme = parsed.scheme.toLowerCase()
            this._scope = parsed.scope ? parsed.scope.toLowerCase() : null
            const [zone, resourceName] = DefaultCRI.splitZone(parsed.resourceName.toLowerCase())
            this._zone = zone
            this._resourceName = resourceName
            this._path = parsed.path
            this._version = parsed.version
        } else if (args.length === 5) {
            const [scheme, scope, resourceName, path, version] = args
            this._scheme = scheme.toLowerCase()
            this._scope = scope ? scope.toLowerCase() : null
            const [zone, name] = DefaultCRI.splitZone((resourceName as string).toLowerCase())
            this._zone = zone
            this._resourceName = name
            this._path = path
            this._version = version
        } else {
            throw new Error("Invalid constructor arguments for DefaultCRI")
        }
        this._raw = DefaultCRI.buildRaw(this._scheme, this._scope, this._zone, this._resourceName, this._path, this._version)

        if (!this._scheme || !this._resourceName) {
            throw new Error(`Invalid CRI: scheme and resourceName are required. Got: ${this._raw}`)
        }
        // '~' delimits the zone from the resourceName, so any other occurrence could only confuse
        // zone parsing — a scope carrying one, or a second '~' in the host part, is rejected outright
        if (this._scope !== null && this._scope.includes(ZONE_DELIMITER)) {
            throw new Error(`The scope must not contain '~' but was '${this._scope}'`)
        }
        if (this._resourceName.includes(ZONE_DELIMITER)) {
            throw new Error(`The resourceName must not contain '~' but was '${this._resourceName}'`)
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

    public zone(): string | null {
        return this._zone
    }

    public hasZone(): boolean {
        return this._zone !== null
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
        if (this.hasZone()) {
            result += `${this._zone}${ZONE_DELIMITER}`
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

    // Splits [zone~]resourceName into its zone (or null) and resourceName
    private static splitZone(hostPart: string): [string | null, string] {
        const delimiter = hostPart.indexOf(ZONE_DELIMITER)
        return delimiter >= 0
            ? [hostPart.substring(0, delimiter), hostPart.substring(delimiter + 1)]
            : [null, hostPart]
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
            scheme: scheme as string,
            scope: scope || null,
            resourceName: resourceName as string,
            path: path ? path.substring(1) : null,
            version: version || null,
        }
    }

    // Helper to build a raw CRI string
    private static buildRaw(
        scheme: string,
        scope: string | null,
        zone: string | null,
        resourceName: string,
        path: string | null,
        version: string | null
    ): string {
        let result = `${scheme}://`
        if (scope) {
            result += `${scope}@`
        }
        if (zone) {
            result += `${zone}${ZONE_DELIMITER}`
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
