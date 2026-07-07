import {createCRI, type CRI} from '@/api/event/CRI'
import {EventConstants} from '@/api/event/IEventBus'

export class ServiceIdentifier {

    public namespace: string | null
    public name: string
    public zones?: string[]
    public scope?: string
    public version?: string
    private _cris: CRI[] | null = null


    constructor(namespace: string | null, name: string, zones?: string[]) {
        this.namespace = namespace
        this.name = name
        this.zones = zones
    }

    /**
     * Returns the fully qualified name this {@link ServiceIdentifier} is addressed by
     * This is the zone.namespace.name, omitting any part that is not set
     * @return string containing the qualified name
     * @throws if this {@link ServiceIdentifier} has more than one zone and therefore more than
     *         one qualified name
     */
    public qualifiedName(): string {
        if (this.zones && this.zones.length > 1) {
            throw new Error(`ServiceIdentifier has ${this.zones.length} zones so there is no single qualifiedName`)
        }
        return this.qualifiedNameFor(this.zones?.[0])
    }

    /**
     * The {@link CRI}s this {@link ServiceIdentifier} is addressable by, one per zone
     * @return the cris for this {@link ServiceIdentifier}, never empty
     */
    public cris(): CRI[] {
        if (this._cris == null) {
            const zones: (string | undefined)[] = this.zones && this.zones.length > 0 ? this.zones : [undefined]
            this._cris = zones.map(zone => createCRI(
                EventConstants.SERVICE_DESTINATION_SCHEME, // scheme
                this.scope || null,                       // scope
                this.qualifiedNameFor(zone),              // resourceName
                null,                                     // path
                this.version || null                      // version
            ))
        }
        return this._cris
    }

    /**
     * The {@link CRI} that represents this {@link ServiceIdentifier}
     * @return the cri for this {@link ServiceIdentifier}
     * @throws if this {@link ServiceIdentifier} has more than one zone and therefore more than
     *         one cri
     */
    public cri(): CRI {
        const cris = this.cris()
        if (cris.length !== 1) {
            throw new Error(`ServiceIdentifier has ${cris.length} cris so there is no single cri`)
        }
        return cris[0]
    }

    private qualifiedNameFor(zone: string | undefined): string {
        const name = this.namespace ? this.namespace + "." + this.name : this.name
        return zone ? zone + "." + name : name
    }
}
