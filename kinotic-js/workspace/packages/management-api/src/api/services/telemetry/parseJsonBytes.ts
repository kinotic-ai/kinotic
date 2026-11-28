/** Parses the raw JSON bytes a backend answered with, as a service replies with them. */
export function parseJsonBytes(bytes: Uint8Array): any {
    return JSON.parse(new TextDecoder().decode(bytes))
}
