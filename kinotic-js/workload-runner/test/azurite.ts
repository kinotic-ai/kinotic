import { createHmac } from 'node:crypto'

/**
 * Azurite, the Azure Storage emulator, as the tests that write through a SAS reach it: its
 * well-known development account where AZURITE_BLOB_ENDPOINT points or on its default port.
 */

const ACCOUNT = 'devstoreaccount1'
const ACCOUNT_KEY = 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=='
const SAS_VERSION = '2020-12-06'

export const ENDPOINT = process.env.AZURITE_BLOB_ENDPOINT ?? `http://127.0.0.1:10000/${ACCOUNT}`

/** Shared-key headers for a request to the emulator, whose URL path starts with the account the canonical resource repeats. */
export function sharedKeyHeaders(method: string, url: string, contentLength: number = 0, extra: Record<string, string> = {}): Record<string, string> {
    const date = new Date().toUTCString()
    const headers: Record<string, string> = { 'x-ms-date': date, 'x-ms-version': SAS_VERSION, ...extra }
    const canonicalHeaders = Object.keys(headers)
        .filter(name => name.startsWith('x-ms-'))
        .sort()
        .map(name => `${name}:${headers[name]}`)
        .join('\n')
    const { pathname, searchParams } = new URL(url)
    const canonicalQuery = [...searchParams.keys()].sort()
        .map(name => `${name.toLowerCase()}:${searchParams.get(name)}`)
        .join('\n')
    const canonicalResource = `/${ACCOUNT}${pathname}${canonicalQuery ? '\n' + canonicalQuery : ''}`
    const stringToSign = [method, '', '', contentLength ? String(contentLength) : '', '', '', '', '', '', '', '', '',
                          canonicalHeaders, canonicalResource].join('\n')
    const signature = createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
    headers.Authorization = `SharedKey ${ACCOUNT}:${signature}`
    return headers
}

/** A container SAS allowing create, write, delete and list for an hour, the permissions the platform's upload URLs carry. */
export function containerSas(container: string): string {
    const start = new Date(Date.now() - 5 * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z')
    const expiry = new Date(Date.now() + 60 * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z')
    const permissions = 'cwdl'
    const stringToSign = [permissions, start, expiry, `/blob/${ACCOUNT}/${container}`, '', '', 'https,http', SAS_VERSION,
                          'c', '', '', '', '', '', '', ''].join('\n')
    const signature = createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
    const query = new URLSearchParams({ sp: permissions, st: start, se: expiry, spr: 'https,http', sv: SAS_VERSION, sr: 'c', sig: signature })
    return query.toString()
}

export async function azuriteUp(): Promise<boolean> {
    try {
        const url = `${ENDPOINT}?comp=list`
        const response = await fetch(url, { headers: sharedKeyHeaders('GET', url) })
        return response.ok
    } catch {
        return false
    }
}

/** Creates the container unless it exists. */
export async function createContainer(container: string): Promise<void> {
    const url = `${ENDPOINT}/${container}?restype=container`
    const created = await fetch(url, { method: 'PUT', headers: sharedKeyHeaders('PUT', url) })
    if (created.status !== 201 && created.status !== 409) {
        throw new Error(`creating container ${container} failed with ${created.status}`)
    }
}

/** Writes a blob stamped with the commit, the way a workload writes one. */
export async function writeBlob(container: string, path: string, body: string, commitSha: string): Promise<void> {
    const url = `${ENDPOINT}/${container}/${path}`
    const written = await fetch(url, { method: 'PUT', body,
        headers: sharedKeyHeaders('PUT', url, Buffer.byteLength(body), { 'x-ms-blob-type': 'BlockBlob', 'x-ms-meta-commit': commitSha }) })
    if (written.status !== 201) {
        throw new Error(`writing ${path} failed with ${written.status}`)
    }
}

export async function readBlob(container: string, path: string): Promise<Response> {
    const url = `${ENDPOINT}/${container}/${path}`
    return fetch(url, { headers: sharedKeyHeaders('GET', url) })
}
