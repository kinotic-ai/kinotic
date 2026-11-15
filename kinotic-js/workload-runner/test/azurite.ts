/**
 * Reaches the Azurite emulator as the publish and removal workloads do, with container SASes signed the way the
 * system server signs them.
 */
import { createHmac } from 'node:crypto'

/** Azurite's well-known development account, reached where AZURITE_BLOB_ENDPOINT points or on its default port. */
const ACCOUNT = 'devstoreaccount1'
const ACCOUNT_KEY = 'Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=='
const ENDPOINT = process.env.AZURITE_BLOB_ENDPOINT ?? `http://127.0.0.1:10000/${ACCOUNT}`
const CONTAINER = 'sites'
const SAS_VERSION = '2020-12-06'

function sign(stringToSign: string): string {
    return createHmac('sha256', Buffer.from(ACCOUNT_KEY, 'base64')).update(stringToSign, 'utf-8').digest('base64')
}

/** A SAS for the sites container, valid for an hour unless an expiry is given. */
function containerSas(permissions: string, expiry: Date = new Date(Date.now() + 60 * 60_000)): string {
    const se = expiry.toISOString().replace(/\.\d{3}Z$/, 'Z')
    const stringToSign = [permissions, '', se, `/blob/${ACCOUNT}/${CONTAINER}`, '', '', '', SAS_VERSION,
                          'c', '', '', '', '', '', '', ''].join('\n')
    return new URLSearchParams({ sv: SAS_VERSION, se, sr: 'c', sp: permissions, sig: sign(stringToSign) }).toString()
}

/** The URL of a site's directory with a SAS, the shape of the upload and removal URLs the system issues. */
export function siteUrl(directory: string, permissions: string = 'cwdl', expiry?: Date): string {
    return `${ENDPOINT}/${CONTAINER}/${directory}?${containerSas(permissions, expiry)}`
}

/** Whether Azurite answers, creating the sites container when it doesn't exist. */
export async function azuriteUp(): Promise<boolean> {
    try {
        const url = `${ENDPOINT}/${CONTAINER}?restype=container`
        const date = new Date().toUTCString()
        const stringToSign = ['PUT', '', '', '', '', '', '', '', '', '', '', '',
                              `x-ms-date:${date}\nx-ms-version:${SAS_VERSION}`, `/${ACCOUNT}${new URL(url).pathname}\nrestype:container`].join('\n')
        const response = await fetch(url, {
            method: 'PUT',
            headers: { 'x-ms-date': date, 'x-ms-version': SAS_VERSION, Authorization: `SharedKey ${ACCOUNT}:${sign(stringToSign)}` },
        })
        return response.status === 201 || response.status === 409
    } catch {
        return false
    }
}

export async function putBlob(path: string, body: string, headers: Record<string, string> = {}): Promise<Response> {
    return fetch(`${ENDPOINT}/${CONTAINER}/${path}?${containerSas('w')}`,
                 { method: 'PUT', body, headers: { 'x-ms-blob-type': 'BlockBlob', ...headers } })
}

export async function readBlob(path: string): Promise<Response> {
    return fetch(`${ENDPOINT}/${CONTAINER}/${path}?${containerSas('r')}`)
}
