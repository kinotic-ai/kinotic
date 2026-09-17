import type { ChildProcess } from 'node:child_process'
import { closeSync, existsSync, openSync, renameSync, rmSync, statSync, writeSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Output of the runner and of the process it runs. Everything goes to the runner's own
 * stdout and stderr, and additionally to a size-rotated file when the node asks for one:
 * a node whose VM runtime captures no stdout mounts a log directory into the guest and
 * names it in the environment, and the files under it are what that node ships.
 *
 * Environment:
 * - KINOTIC_LOG_DIR          directory to write {@link LOG_FILE} into; unset means stdout only
 * - KINOTIC_LOG_MAX_SIZE_MB  size at which the file rotates; required with the directory
 * - KINOTIC_LOG_MAX_FILES    rotated files kept beside it; required with the directory
 */

/**
 * Name of the current log file. Rotated files are {@code workload.log.1} and up, which
 * deliberately do not end in {@code .log}: the node tails every {@code *.log} under the
 * directory, and a rotation must not surface as a new file to ingest from the start.
 */
export const LOG_FILE = 'workload.log'

class RotatingLogFile {

    private fd: number
    private size: number

    constructor(private readonly path: string,
                private readonly maxBytes: number,
                private readonly maxFiles: number) {
        this.fd = openSync(path, 'a')
        this.size = statSync(path).size
    }

    write(chunk: Uint8Array): void {
        if (this.size > 0 && this.size + chunk.byteLength > this.maxBytes) {
            this.rotate()
        }
        writeSync(this.fd, chunk)
        this.size += chunk.byteLength
    }

    private rotate(): void {
        closeSync(this.fd)
        rmSync(`${this.path}.${this.maxFiles}`, { force: true })
        for (let i = this.maxFiles - 1; i >= 1; i--) {
            if (existsSync(`${this.path}.${i}`)) {
                renameSync(`${this.path}.${i}`, `${this.path}.${i + 1}`)
            }
        }
        if (this.maxFiles > 0) {
            renameSync(this.path, `${this.path}.1`)
        } else {
            rmSync(this.path)
        }
        this.fd = openSync(this.path, 'a')
        this.size = 0
    }
}

function openFromEnvironment(): RotatingLogFile | null {
    const dir = process.env.KINOTIC_LOG_DIR
    let ret: RotatingLogFile | null = null
    if (dir) {
        const maxSizeMb = Number(process.env.KINOTIC_LOG_MAX_SIZE_MB)
        const maxFiles = Number(process.env.KINOTIC_LOG_MAX_FILES)
        if (!Number.isFinite(maxSizeMb) || maxSizeMb <= 0 || !Number.isInteger(maxFiles) || maxFiles < 0) {
            throw new Error('KINOTIC_LOG_DIR is set without a valid KINOTIC_LOG_MAX_SIZE_MB and KINOTIC_LOG_MAX_FILES')
        }
        ret = new RotatingLogFile(join(dir, LOG_FILE), maxSizeMb * 1024 * 1024, maxFiles)
    }
    return ret
}

const file = openFromEnvironment()

function emit(stream: NodeJS.WriteStream, chunk: string | Uint8Array): void {
    stream.write(chunk)
    file?.write(typeof chunk === 'string' ? Buffer.from(chunk) : chunk)
}

export function log(message: string): void {
    emit(process.stdout, `${message}\n`)
}

export function logError(message: string): void {
    emit(process.stderr, `${message}\n`)
}

/**
 * Forwards a child's piped stdout and stderr to the runner's own. The child must have been
 * spawned with both streams piped.
 */
export function forwardOutput(child: ChildProcess): void {
    child.stdout?.on('data', chunk => emit(process.stdout, chunk))
    child.stderr?.on('data', chunk => emit(process.stderr, chunk))
}
