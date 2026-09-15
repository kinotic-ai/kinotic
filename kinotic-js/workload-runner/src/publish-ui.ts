import { readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { findArtifacts } from './artifacts.ts'
import { log, logError } from './log.ts'
import { blobUrl, deleteFilesOfOtherCommits, parseSiteUrl, type SiteTarget } from './site-storage.ts'

/**
 * One-shot entrypoint of the UI publish workload: uploads every built UI of the checkout to
 * its site's directory in the platform's sites storage account, through the upload URL
 * issued for that site. Per UI, {@code dist} goes up as it is, every blob stamped with the
 * commit it belongs to: the hashed files under {@code assets/} first, marked immutable, then
 * the rest uncached, then {@code version.json}, then {@code index.html} last, so a site never
 * serves an index whose assets are not there yet and the index switch is the atomic publish.
 * Once the index has switched, the files other commits left in the directory are deleted.
 *
 * Environment:
 * - KINOTIC_UI_UPLOAD_URLS  a JSON object of UI name to upload URL: the site's directory in
 *                           the sites account, with a SAS for that directory as its query (required)
 * - KINOTIC_UI_COMMIT       the commit the UIs were built from (required)
 * - KINOTIC_WORKSPACE_DIR   the checkout, mounted read-only (default /workspace)
 * - KINOTIC_LOG_*           see log.ts
 */

/** Files under assets/ carry a content hash in their name and never change, so caches may keep them for a year. */
const IMMUTABLE_CACHE_CONTROL = 'public, max-age=31536000, immutable'
/** Everything else, the index and the version file among it, changes on a publish and must always be fetched fresh. */
const NO_CACHE_CONTROL = 'no-cache'
const UPLOAD_CONCURRENCY = 4
const ASSETS_DIR = 'assets/'
const VERSION_FILE = 'version.json'
const INDEX_FILE = 'index.html'
/** The blob metadata naming the commit a file was published by, which the deploy's cleanup reads. */
const COMMIT_METADATA_HEADER = 'x-ms-meta-commit'

function require_(name: string): string {
    const value = process.env[name]
    if (!value) {
        throw new Error(`${name} must be set`)
    }
    return value
}

/** The upload URL of every UI, by name. */
function parseUploadUrls(json: string): Map<string, SiteTarget> {
    const urls = JSON.parse(json) as Record<string, string>
    return new Map(Object.entries(urls).map(([name, url]) => [name, parseSiteUrl(`the upload URL of UI ${name}`, url)]))
}

/** Every file under dir, as paths relative to it with forward slashes. */
function walk(dir: string, root: string = dir): string[] {
    const out: string[] = []
    for (const entry of readdirSync(dir)) {
        const path = join(dir, entry)
        if (statSync(path).isDirectory()) {
            out.push(...walk(path, root))
        } else {
            out.push(relative(root, path).split('\\').join('/'))
        }
    }
    return out
}

/**
 * Uploads one blob as a block blob with its cache policy and the type Bun infers from the
 * name. A 5xx is retried once; anything else that is not 2xx fails the publish.
 */
async function upload(url: string, body: Blob, cacheControl: string, contentType: string, commitSha: string): Promise<void> {
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

async function uploadAll(tasks: Array<() => Promise<void>>): Promise<void> {
    let next = 0
    const workers = Array.from({ length: Math.min(UPLOAD_CONCURRENCY, tasks.length) }, async () => {
        while (next < tasks.length) {
            const task = tasks[next++]!
            await task()
        }
    })
    await Promise.all(workers)
}

async function publishUi(target: SiteTarget, workspaceDir: string, name: string, dir: string, commitSha: string): Promise<void> {
    const dist = join(workspaceDir, dir, 'dist')
    const files = walk(dist)
    if (!files.includes(INDEX_FILE)) {
        throw new Error(`UI ${name} (${dir}) has no dist/${INDEX_FILE}; was it built?`)
    }
    const assets = files.filter(file => file.startsWith(ASSETS_DIR))
    const others = files.filter(file => !file.startsWith(ASSETS_DIR) && file !== INDEX_FILE && file !== VERSION_FILE)
    log(`[workload-runner] publishing UI ${name}: ${assets.length} asset(s) and ${others.length} other file(s) of ${commitSha}`)
    const uploadFile = (file: string, cacheControl: string) => () => {
        const blob = Bun.file(join(dist, file))
        return upload(blobUrl(target, ...file.split('/')), blob, cacheControl, blob.type || 'application/octet-stream', commitSha)
    }
    await uploadAll(assets.map(file => uploadFile(file, IMMUTABLE_CACHE_CONTROL)))
    await uploadAll(others.map(file => uploadFile(file, NO_CACHE_CONTROL)))
    await upload(blobUrl(target, VERSION_FILE), new Blob([JSON.stringify({ commitSha })]),
                 NO_CACHE_CONTROL, 'application/json', commitSha)
    const index = Bun.file(join(dist, INDEX_FILE))
    await upload(blobUrl(target, INDEX_FILE), index, NO_CACHE_CONTROL, index.type || 'text/html', commitSha)
    // the index switched to this commit, so nothing reaches another commit's files
    const stale = await deleteFilesOfOtherCommits(target, commitSha)
    log(`[workload-runner] published UI ${name}; deleted ${stale} file(s) of other commits`)
}

async function main(): Promise<void> {
    const targets = parseUploadUrls(require_('KINOTIC_UI_UPLOAD_URLS'))
    const commitSha = require_('KINOTIC_UI_COMMIT')
    const workspaceDir = process.env.KINOTIC_WORKSPACE_DIR ?? '/workspace'

    const uis = findArtifacts(workspaceDir).uis
    log(`[workload-runner] publishing ${uis.length} UI(s) of ${commitSha}`)
    for (const ui of uis) {
        const target = targets.get(ui.name)
        if (!target) {
            throw new Error(`UI ${ui.name} has no upload URL; the deploy issued URLs for ${[...targets.keys()].join(', ') || 'no UI'}`)
        }
        await publishUi(target, workspaceDir, ui.name, ui.dir, commitSha)
    }
    log(`[workload-runner] published ${commitSha}`)
}

try {
    await main()
} catch (error) {
    logError(`[workload-runner] publish failed: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
}
