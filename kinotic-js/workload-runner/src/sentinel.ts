import { mkdirSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'

/**
 * Path of the reload sentinel relative to the project checkout. The sync run writes the
 * deployed commit sha here as its final step, and the supervisor polls it for changes —
 * polled rather than watched because inotify events do not cross the VM boundary between
 * the two workloads sharing the checkout.
 */
export const SENTINEL_RELATIVE_PATH = '.kinotic/reload'

export function sentinelPath(checkoutDir: string): string {
    return join(checkoutDir, SENTINEL_RELATIVE_PATH)
}

/**
 * Writes the sentinel atomically (write + rename), so the supervisor can never read a
 * half-written value.
 */
export function writeSentinel(checkoutDir: string, commitSha: string): void {
    const file = sentinelPath(checkoutDir)
    mkdirSync(dirname(file), { recursive: true })
    writeFileSync(`${file}.tmp`, commitSha)
    renameSync(`${file}.tmp`, file)
}
