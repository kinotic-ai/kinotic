import { log } from './log.ts'

/**
 * What the entrypoints do with one directory of a platform storage account, a site's directory
 * in the sites account or a project's SBOM directory in the organization storage account, through
 * a URL that names the directory and carries a SAS scoped to it:
 * `<account URL>/<container>/<directory>?<sas>`, the directory being everything after the
 * container. Files are written, listed and deleted through the account's blob endpoint, and a
 * directory is deleted through its Data Lake endpoint, which removes it and everything under it
 * in one request.
 */

/** One directory and the credential that acts on it. */
export interface BlobDirectory {
    /** The container's URL on the blob endpoint, e.g. https://stkinoticsites.blob.core.windows.net/sites */
    containerUrl: string
    /** The container's URL on the Data Lake endpoint, the same host on dfs */
    dfsContainerUrl: string
    /** The directory's path in the container */
    directory: string
    /** The SAS query, without the leading ? */
    query: string
}

/** A blob under the directory as the listing describes it. */
export interface DirectoryBlob {
    name: string
    isDirectory: boolean
    /** The commit that wrote it, from its metadata; absent on a directory or an unstamped blob */
    commit?: string
}

/** The blob metadata naming the commit a file was written for, which the cleanup of other commits' files reads. */
const COMMIT_METADATA_HEADER = 'x-ms-meta-commit'

export function parseDirectoryUrl(name: string, url: string): BlobDirectory {
    const query = url.indexOf('?')
    if (query === -1) {
        throw new Error(`${name} carries no SAS query`)
    }
    const path = url.slice(0, query)
    const parsed = path.includes('://') ? new URL(path) : null
    // an account on an IP address or localhost, an emulator's, is named by the first path
    // segment rather than the host, the way Azure's own clients read such a URL
    const accountSegments = parsed !== null && /^(localhost|\[.*\]|[\d.]+)$/.test(parsed.hostname) ? 1 : 0
    const segments = parsed?.pathname.split('/').filter(segment => segment !== '') ?? []
    if (parsed === null || segments.length < accountSegments + 2) {
        throw new Error(`${name} names no directory in a container: ${path}`)
    }
    const containerUrl = `${parsed.origin}/${segments.slice(0, accountSegments + 1).join('/')}`
    return {
        containerUrl,
        // the Data Lake endpoint of an account is its blob host on dfs
        dfsContainerUrl: containerUrl.replace('.blob.', '.dfs.'),
        directory: segments.slice(accountSegments + 1).join('/'),
        query: url.slice(query + 1),
    }
}

/** The URL of one file under the directory, for a PUT or DELETE through the blob endpoint. */
export function blobUrl(directory: BlobDirectory, ...segments: string[]): string {
    return `${directory.containerUrl}/${directory.directory}/${segments.map(encodeURIComponent).join('/')}?${directory.query}`
}

/**
 * Uploads one blob as a block blob with its cache policy and content type, stamped with the
 * commit it belongs to. A 5xx is retried once; anything else that is not 2xx fails the upload.
 */
export async function uploadBlob(url: string, body: Blob, cacheControl: string, contentType: string, commitSha: string): Promise<void> {
    for (let attempt = 1; ; attempt++) {
        const response = await fetch(url, {
            method: 'PUT',
            headers: {
                'x-ms-blob-type': 'BlockBlob',
                'x-ms-blob-cache-control': cacheControl,
                'Content-Type': contentType,
                [COMMIT_METADATA_HEADER]: commitSha,
            },
            body,
        })
        if (response.ok) {
            return
        }
        const detail = `${response.status} ${await response.text()}`
        if (response.status >= 500 && attempt === 1) {
            log(`[workload-runner] retrying upload after ${detail}`)
            continue
        }
        throw new Error(`upload failed with ${detail}`)
    }
}

/** Every blob under the directory, with the commit that wrote it. */
export async function listBlobs(directory: BlobDirectory): Promise<DirectoryBlob[]> {
    const blobs: DirectoryBlob[] = []
    let marker = ''
    do {
        const url = `${directory.containerUrl}?restype=container&comp=list&include=metadata`
            + `&prefix=${encodeURIComponent(directory.directory + '/')}${marker ? `&marker=${encodeURIComponent(marker)}` : ''}&${directory.query}`
        const response = await fetch(url)
        if (!response.ok) {
            throw new Error(`listing ${directory.directory} failed with ${response.status} ${await response.text()}`)
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

/** Deletes one blob under the directory; one already gone is not a failure. */
export async function deleteBlob(directory: BlobDirectory, name: string): Promise<void> {
    const response = await fetch(`${directory.containerUrl}/${name.split('/').map(encodeURIComponent).join('/')}?${directory.query}`,
                                 { method: 'DELETE' })
    if (!response.ok && response.status !== 404) {
        throw new Error(`deleting ${name} failed with ${response.status} ${await response.text()}`)
    }
}

/**
 * Deletes every file under the directory that was written for another commit, then the
 * directories that are empty for it. Files of the given commit stay.
 */
export async function deleteFilesOfOtherCommits(directory: BlobDirectory, commitSha: string): Promise<number> {
    const blobs = await listBlobs(directory)
    const stale = blobs.filter(blob => !blob.isDirectory && blob.commit !== commitSha)
    for (const blob of stale) {
        await deleteBlob(directory, blob.name)
    }
    // a directory on the hierarchical account is deletable only once empty: deepest first,
    // and one still holding a kept file stays
    const directories = blobs.filter(blob => blob.isDirectory).sort((a, b) => b.name.length - a.name.length)
    for (const nested of directories) {
        await deletePath(directory, nested.name, false, [404, 409])
    }
    return stale.length
}

/** Deletes the directory and everything under it; one already gone is not a failure. */
export async function deleteDirectory(directory: BlobDirectory): Promise<void> {
    await deletePath(directory, directory.directory, true, [404])
}

async function deletePath(directory: BlobDirectory, path: string, recursive: boolean, tolerated: number[]): Promise<void> {
    const response = await fetch(`${directory.dfsContainerUrl}/${path.split('/').map(encodeURIComponent).join('/')}?recursive=${recursive}&${directory.query}`,
                                 { method: 'DELETE' })
    if (!response.ok && !tolerated.includes(response.status)) {
        throw new Error(`deleting ${path} failed with ${response.status} ${await response.text()}`)
    }
}

function unescapeXml(text: string): string {
    return text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&')
}
