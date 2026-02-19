import {createCRI, CRI} from '@/core/api/CRI.js'
import {EventConstants} from '@/core/api/IEventBus.js'

export class ServiceIdentifier {

    public namespace: string
    public name: string
    public scope?: string
    public version?: string
    private _cri: CRI | null = null


    constructor(namespace: string, name: string) {
        this.namespace = namespace
        this.name = name
    }

    /**
     * Returns the qualified name for this {@link ServiceIdentifier}
     * This is the namespace.name
     * @return string containing the qualified name
     */
    public qualifiedName(): string{
        return this.namespace + "." + this.name;
    }

    /**
     * The {@link CRI} that represents this {@link ServiceIdentifier}
     * @return the cri for this {@link ServiceIdentifier}
     */
    public cri(): CRI {
        if(this._cri == null) {
            this._cri = createCRI(
                EventConstants.SERVICE_DESTINATION_SCHEME, // scheme
                this.scope || null,                       // scope
                this.qualifiedName(),                     // resourceName
                null,                                     // path (null as per your example)
                this.version || null                      // version
            );
        }
        return this._cri;
    }
}
