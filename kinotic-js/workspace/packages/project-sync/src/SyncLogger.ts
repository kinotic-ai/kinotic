/**
 * Logging contract synchronization reports progress through. The CLI passes its oclif
 * command; non-interactive callers use {@link ConsoleSyncLogger}.
 */
export interface SyncLogger {
    log(message: string): void

    logVerbose(message: string | (() => string), verbose: boolean): void
}

export class ConsoleSyncLogger implements SyncLogger {

    log(message: string): void {
        console.log(message)
    }

    logVerbose(message: string | (() => string), verbose: boolean): void {
        if (verbose) {
            console.log(typeof message === 'function' ? message() : message)
        }
    }
}
