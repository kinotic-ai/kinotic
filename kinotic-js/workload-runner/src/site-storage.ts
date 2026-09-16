/**
 * What the publish and removal entrypoints do with one site's directory in the platform's
 * sites storage account, through a URL that names the directory and carries a SAS scoped to
 * it: `<container URL>/<directory>?<sas>`, the directory being the URL's last path segment.
 * Files are listed and deleted through the account's blob endpoint, and a directory is deleted
 * through its Data Lake endpoint, which removes it and everything under it in one request.
 */

/** One site's directory and the credential that acts on it. */
export interface SiteTarget {
    /** The container's URL on the blob endpoint, e.g. https://stkinoticsites.blob.core.windows.net/sites */
    containerUrl: string
    /** The container's URL on the Data Lake endpoint, the same host on dfs */
    dfsContainerUrl: string
    /** The site's directory in the container: the last segment of the URL's path */
    directory: string
    /** The SAS query, without the leading ? */
    query: string
}

/** A blob under the site's directory as the listing describes it. */
export interface SiteBlob {
    name: string
    isDirectory: boolean
    /** The commit that published it, from its metadata; absent on a directory or an unstamped blob */
    commit?: string
}

export function parseSiteUrl(name: string, url: string): SiteTarget {
    const query = url.indexOf('?')
    if (query === -1) {
        throw new Error(`${name} carries no SAS query`)
    }
    const path = url.slice(0, query)
    const slash = path.lastIndexOf('/')
    const directory = path.slice(slash + 1)
    const containerUrl = path.slice(0, slash)
    if (!directory || !containerUrl.includes('://') || new URL(containerUrl).pathname === '/') {
        throw new Error(`${name} names no directory in a container: ${path}`)
    }
    return {
        containerUrl,
        // the Data Lake endpoint of an account is its blob host on dfs
        dfsContainerUrl: containerUrl.replace('.blob.', '.dfs.'),
        directory,
        query: url.slice(query + 1),
    }
}

/** The URL of one file under the site's directory, for a PUT or DELETE through the blob endpoint. */
export function blobUrl(site: SiteTarget, ...segments: string[]): string {
    return `${site.containerUrl}/${site.directory}/${segments.map(encodeURIComponent).join('/')}?${site.query}`
}

/** Every blob under the site's directory, with the commit that published it. */
export async function listBlobs(site: SiteTarget): Promise<SiteBlob[]> {
    const blobs: SiteBlob[] = []
    let marker = ''
    do {
        const url = `${site.containerUrl}?restype=container&comp=list&include=metadata`
            + `&prefix=${encodeURIComponent(site.directory + '/')}${marker ? `&marker=${encodeURIComponent(marker)}` : ''}&${site.query}`
        const response = await fetch(url)
        if (!response.ok) {
            throw new Error(`listing ${site.directory} failed with ${response.status} ${await response.text()}`)
        }
        const xml = await response.text()
        for (const entry of xml.matchAll(/<Blob>([\s\S]*?)<\/Blob>/g)) {
            const body = entry[1]!
            blobs.push({
                name: unescapeXml(body.match(/<Name>([^<]*)<\/Name>/)![1]!),
                isDirectory: /<hdi_isfolder>true<\/hdi_isfolder>/.test(body),
                commit: body.match(/<commit>([^<]*)<\/commit>/)?.[1],
            })
        }
        marker = xml.match(/<NextMarker>([^<]*)<\/NextMarker>/)?.[1] ?? ''
    } while (marker)
    return blobs
}

/** Deletes one blob under the site's directory; one already gone is not a failure. */
export async function deleteBlob(site: SiteTarget, name: string): Promise<void> {
    const response = await fetch(`${site.containerUrl}/${name.split('/').map(encodeURIComponent).join('/')}?${site.query}`,
                                 { method: 'DELETE' })
    if (!response.ok && response.status !== 404) {
        throw new Error(`deleting ${name} failed with ${response.status} ${await response.text()}`)
    }
}

/**
 * Deletes every file of the site that a publish of another commit wrote, then the directories
 * that are empty for it. Files of the given commit stay.
 */
export async function deleteFilesOfOtherCommits(site: SiteTarget, commitSha: string): Promise<number> {
    const blobs = await listBlobs(site)
    const stale = blobs.filter(blob => !blob.isDirectory && blob.commit !== commitSha)
    for (const blob of stale) {
        await deleteBlob(site, blob.name)
    }
    // a directory on the hierarchical account is deletable only once empty: deepest first,
    // and one still holding a kept file stays
    const directories = blobs.filter(blob => blob.isDirectory).sort((a, b) => b.name.length - a.name.length)
    for (const directory of directories) {
        await deletePath(site, directory.name, false, [404, 409])
    }
    return stale.length
}

/** Deletes the site's directory and everything under it; one already gone is not a failure. */
export async function deleteDirectory(site: SiteTarget): Promise<void> {
    await deletePath(site, site.directory, true, [404])
}

async function deletePath(site: SiteTarget, path: string, recursive: boolean, tolerated: number[]): Promise<void> {
    const response = await fetch(`${site.dfsContainerUrl}/${path.split('/').map(encodeURIComponent).join('/')}?recursive=${recursive}&${site.query}`,
                                 { method: 'DELETE' })
    if (!response.ok && !tolerated.includes(response.status)) {
        throw new Error(`deleting ${path} failed with ${response.status} ${await response.text()}`)
    }
}

function unescapeXml(text: string): string {
    return text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&')
}
