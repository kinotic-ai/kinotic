import {PrettyPrintableError} from '@oclif/core/lib/errors'

export type Logger = {
    log: (message?: string, ...args: any[]) => void
    warn(input: string | Error): string | Error
    error(input: string | Error, options: {
                                              code?: string;
                                              exit: false;
                                          } & PrettyPrintableError): void
    error(input: string | Error, options?: {
                                               code?: string;
                                               exit?: number;
                                           } & PrettyPrintableError): never

    logVerbose(message: string | ( () => string ), verbose: boolean): void

}

export class ConsoleLogger implements Logger {

    log(message?: string, ...args: any[]): void {
        console.log(message, ...args)
    }

    warn(input: string | Error): string | Error {
        const message = input instanceof Error ? input.message : input
        console.warn(message)
        return input
    }

    error(input: string | Error, options: {
                                              code?: string
                                              exit: false
                                          } & PrettyPrintableError): void
    error(input: string | Error, options?: {
                                               code?: string
                                               exit?: number
                                           } & PrettyPrintableError): never
    error(input: string | Error, options?: any): void | never {
        const message = input instanceof Error ? input.message : input
        console.error('Error:', message)

        if (options) {
            if (options.code) {
                console.error('Code:', options.code)
            }
            if (options.ref) {
                console.error('Reference:', options.ref)
            }
            if (options.suggestions && options.suggestions.length > 0) {
                console.error('Suggestions:')
                options.suggestions.forEach((suggestion: any) => console.error(`- ${suggestion}`))
            }
        }

        if (options?.exit !== undefined && options.exit !== false) {
            process.exit(typeof options.exit === 'number' ? options.exit : 1)
        }
    }

    logVerbose(message: string | (() => string), verbose: boolean): void {
        if (verbose) {
            const output = typeof message === 'function' ? message() : message
            console.log(output)
        }
    }
}
