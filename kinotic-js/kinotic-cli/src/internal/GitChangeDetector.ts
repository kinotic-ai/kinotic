import { execSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import { Logger } from './Logger'

const LAST_GENERATION_FILE = '.kinotic/last-generation'

/**
 * Detects changed files within entity paths using git.
 * Stores the commit hash of the last successful generation to determine
 * which files have changed since then.
 */
export class GitChangeDetector {

    private readonly logger: Logger

    constructor(logger: Logger) {
        this.logger = logger
    }

    /**
     * Returns the set of absolute file paths that have changed within the given entity paths
     * since the last generation, or null if a full scan is needed (no git, first run, etc.).
     */
    getChangedEntityFiles(entitiesPaths: string[]): Set<string> | null {
        if (!this.isGitRepo()) {
            this.logger.log('Not a git repository, performing full scan')
            return null
        }

        const lastHash = this.readLastGenerationHash()
        if (!lastHash) {
            this.logger.log('No previous generation found, performing full scan')
            return null
        }

        // Verify the stored hash still exists in git history
        if (!this.isValidCommit(lastHash)) {
            this.logger.log('Previous generation commit no longer exists, performing full scan')
            return null
        }

        const changedFiles = new Set<string>()

        // Files changed between last generation commit and HEAD
        const committedChanges = this.getGitOutput(`git diff --name-only ${lastHash} HEAD`)
        for (const file of committedChanges) {
            changedFiles.add(path.resolve(file))
        }

        // Unstaged changes in working tree
        const unstagedChanges = this.getGitOutput('git diff --name-only')
        for (const file of unstagedChanges) {
            changedFiles.add(path.resolve(file))
        }

        // Staged but not committed changes
        const stagedChanges = this.getGitOutput('git diff --cached --name-only')
        for (const file of stagedChanges) {
            changedFiles.add(path.resolve(file))
        }

        // New untracked files
        const untrackedFiles = this.getGitOutput('git ls-files --others --exclude-standard')
        for (const file of untrackedFiles) {
            changedFiles.add(path.resolve(file))
        }

        // Filter to only files within entity paths
        const absEntitiesPaths = entitiesPaths.map(p => {
            const abs = path.resolve(p)
            return abs.endsWith(path.sep) ? abs : abs + path.sep
        })

        const relevantFiles = new Set<string>()
        for (const file of changedFiles) {
            for (const entitiesPath of absEntitiesPaths) {
                if (file.startsWith(entitiesPath) && file.endsWith('.ts')) {
                    relevantFiles.add(file)
                    break
                }
            }
        }

        return relevantFiles
    }

    /**
     * Saves the current HEAD commit hash as the last generation point.
     */
    saveLastGenerationHash(): void {
        if (!this.isGitRepo()) {
            return
        }

        try {
            const hash = execSync('git rev-parse HEAD', { encoding: 'utf-8' }).trim()
            const filePath = path.resolve(LAST_GENERATION_FILE)
            fs.mkdirSync(path.dirname(filePath), { recursive: true })
            fs.writeFileSync(filePath, hash)
        } catch {
            // Non-critical, just skip
        }
    }

    private readLastGenerationHash(): string | null {
        try {
            const filePath = path.resolve(LAST_GENERATION_FILE)
            if (fs.existsSync(filePath)) {
                return fs.readFileSync(filePath, 'utf-8').trim()
            }
        } catch {
            // Ignore read errors
        }
        return null
    }

    private isGitRepo(): boolean {
        try {
            execSync('git rev-parse --is-inside-work-tree', { encoding: 'utf-8', stdio: 'pipe' })
            return true
        } catch {
            return false
        }
    }

    private isValidCommit(hash: string): boolean {
        try {
            execSync(`git cat-file -t ${hash}`, { encoding: 'utf-8', stdio: 'pipe' })
            return true
        } catch {
            return false
        }
    }

    private getGitOutput(command: string): string[] {
        try {
            const output = execSync(command, { encoding: 'utf-8', stdio: ['pipe', 'pipe', 'pipe'] })
            return output.trim().split('\n').filter(line => line.length > 0)
        } catch {
            return []
        }
    }
}
