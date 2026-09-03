/** Docker Hub's registry API host, which differs from the docker.io name images carry. */
const DOCKER_HUB_REGISTRY = 'registry-1.docker.io'

// Every manifest form a registry may answer a tag with, so the digest is of what the tag names
const MANIFEST_MEDIA_TYPES = [
    'application/vnd.oci.image.index.v1+json',
    'application/vnd.docker.distribution.manifest.list.v2+json',
    'application/vnd.oci.image.manifest.v1+json',
    'application/vnd.docker.distribution.manifest.v2+json',
].join(', ')

/**
 * Static utility functions used across the vm-manager.
 */
export class Util {

    /**
     * Whether a workload's image must be pulled before every start. A reference pinning a
     * digest or a tag other than {@code latest} names one immutable image, so the copy the
     * node already holds is that image. A floating reference — no tag, or {@code latest} —
     * names whatever the registry holds now, and only a pull answers that. This is the rule
     * Kubernetes applies as its default imagePullPolicy.
     */
    public static mustPullBeforeStart(reference: string): boolean {
        let ret: boolean
        if (reference.includes('@')) {
            ret = false
        } else {
            // A colon after the last slash separates the tag; one before it belongs to a registry port
            const colon = reference.lastIndexOf(':')
            const tag = colon > reference.lastIndexOf('/') ? reference.slice(colon + 1) : null
            ret = tag === null || tag === 'latest'
        }
        return ret
    }

    /**
     * The reference pinned to the digest its registry serves for it right now, so a runtime
     * that trusts its cache for a tag still fetches what the tag currently names. A reference
     * already carrying a digest is returned as is. Resolution is anonymous, which covers public
     * images on Docker Hub, GHCR, and any registry answering the token challenge without
     * credentials; a registry that cannot be reached or refuses fails the call.
     */
    public static async pinImageReference(reference: string): Promise<string> {
        let ret: string
        if (reference.includes('@')) {
            ret = reference
        } else {
            const colon = reference.lastIndexOf(':')
            const hasTag = colon > reference.lastIndexOf('/')
            const name = hasTag ? reference.slice(0, colon) : reference
            const tag = hasTag ? reference.slice(colon + 1) : 'latest'
            const { host, repository } = Util.splitImageName(name)
            const url = `https://${host}/v2/${repository}/manifests/${tag}`
            let response = await fetch(url, { method: 'HEAD', headers: { Accept: MANIFEST_MEDIA_TYPES } })
            if (response.status === 401) {
                const token = await Util.registryToken(response.headers.get('www-authenticate') ?? '')
                response = await fetch(url, { method: 'HEAD', headers: { Accept: MANIFEST_MEDIA_TYPES, Authorization: `Bearer ${token}` } })
            }
            const digest = response.headers.get('docker-content-digest')
            if (!response.ok || !digest) {
                throw new Error(`Could not resolve image ${reference} at ${host}: HTTP ${response.status}`)
            }
            ret = `${name}@${digest}`
        }
        return ret
    }

    // Docker's naming rules: a first segment with a dot, a port, or "localhost" is a registry
    // host; otherwise the image is on Docker Hub, where a bare name lives under library/
    private static splitImageName(name: string): { host: string, repository: string } {
        const slash = name.indexOf('/')
        const first = slash === -1 ? '' : name.slice(0, slash)
        let ret: { host: string, repository: string }
        if (first && (first.includes('.') || first.includes(':') || first === 'localhost')) {
            ret = { host: first, repository: name.slice(slash + 1) }
        } else {
            ret = { host: DOCKER_HUB_REGISTRY, repository: slash === -1 ? `library/${name}` : name }
        }
        return ret
    }

    // Fetches an anonymous bearer token from the realm the registry named in its challenge,
    // e.g. Bearer realm="https://auth.docker.io/token",service="registry.docker.io",scope="repository:x:pull"
    private static async registryToken(challenge: string): Promise<string> {
        const params = new Map<string, string>()
        for (const match of challenge.matchAll(/(\w+)="([^"]*)"/g)) {
            params.set(match[1]!, match[2]!)
        }
        const realm = params.get('realm')
        if (!realm) {
            throw new Error(`Registry challenge names no token realm: ${challenge}`)
        }
        const query = new URLSearchParams()
        for (const key of ['service', 'scope']) {
            if (params.has(key)) {
                query.set(key, params.get(key)!)
            }
        }
        const response = await fetch(`${realm}?${query}`)
        if (!response.ok) {
            throw new Error(`Registry token request to ${realm} failed: HTTP ${response.status}`)
        }
        const body = await response.json() as { token?: string, access_token?: string }
        const token = body.token ?? body.access_token
        if (!token) {
            throw new Error(`Registry token response from ${realm} carried no token`)
        }
        return token
    }
}
