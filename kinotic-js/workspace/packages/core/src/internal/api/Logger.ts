import debug from 'debug'

/**
 * Logging utilities for the Kinoitc library.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface Logger {
    trace(message: string, ...args: any[]): void
    debug(message: string, ...args: any[]): void
    info(message: string, ...args: any[]): void
    warn(message: string, ...args: any[]): void
    error(message: string, ...args: any[]): void
}

export class NoOpLogger implements Logger {
    trace(_message: string, ..._args: any[]): void {}
    debug(_message: string, ..._args: any[]): void {}
    info(_message: string, ..._args: any[]): void {}
    warn(_message: string, ..._args: any[]): void {}
    error(_message: string, ..._args: any[]): void {}
}

export function createDebugLogger(namespace: string): Logger {
    const debugLogger = debug(namespace)
    return {
        trace: (...args) => debugLogger("TRACE", ...args),
        debug: (...args) => debugLogger("DEBUG", ...args),
        info: (...args) => debugLogger("INFO", ...args),
        warn: (...args) => debugLogger("WARN", ...args),
        error: (...args) => debugLogger("ERROR", ...args),
    }
}
