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
        // The name is the final dot separated label of the address, so a dot inside it would
        // change where the namespace ends when the address is parsed
        if (name.includes('.')) {
            throw new Error(`The name must not contain '.' but was '${name}'`)
        }
        // The namespace forms interior labels of the address; an underscore is illegal in a URI
        // host, so a segment carrying one would make the CRI an invalid URI
        if (namespace != null && namespace.includes('_')) {
            throw new Error(`The namespace must not contain '_' but was '${namespace}'`)
        }
        // '~' delimits the zone from the resourceName in a CRI, so it can never appear inside either part
        if (name.includes('~')) {
            throw new Error(`The name must not contain '~' but was '${name}'`)
        }
        if (namespace != null && namespace.includes('~')) {
            throw new Error(`The namespace must not contain '~' but was '${namespace}'`)
        }
        this.namespace = namespace
        this.name = name
        this.zone = zone
    }

    /**
     * Returns the fully qualified name this {@link ServiceIdentifier} is addressed by
     * This is the zone~namespace.name, omitting any part that is not set
     * @return string containing the qualified name
     */
    public qualifiedName(): string {
        const name = this.namespace ? this.namespace + "." + this.name : this.name
        return this.zone ? this.zone + "~" + name : name
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
