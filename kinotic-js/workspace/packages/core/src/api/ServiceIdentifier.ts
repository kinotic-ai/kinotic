import {createCRI, type CRI} from '@/api/event/CRI'
import {EventConstants} from '@/api/event/IEventBus'

export class ServiceIdentifier {

    public namespace: string | null
    public name: string
    public zone?: string
    public scope?: string
    public version?: string
    private _cri: CRI | null = null


    constructor(namespace: string | null, name: string, zone?: string) {
        this.namespace = namespace
        this.name = name
        this.zone = zone
    }

    /**
     * Returns the fully qualified name this {@link ServiceIdentifier} is addressed by
     * This is the zone.namespace.name, omitting any part that is not set
     * @return string containing the qualified name
     */
    public qualifiedName(): string {
        const name = this.namespace ? this.namespace + "." + this.name : this.name
        return this.zone ? this.zone + "." + name : name
    }

    /**
     * The {@link CRI} that represents this {@link ServiceIdentifier}
     * @return the cri for this {@link ServiceIdentifier}
     */
    public cri(): CRI {
        if (this._cri == null) {
            this._cri = createCRI(
                EventConstants.SERVICE_DESTINATION_SCHEME, // scheme
                this.scope || null,                       // scope
                this.qualifiedName(),                     // resourceName
                null,                                     // path
                this.version || null                      // version
            )
        }
        return this._cri
    }
}
