import fs from 'fs'
import path from 'path'
import simpleGit, { type SimpleGit } from 'simple-git'
import { Logger } from './Logger'

const LAST_GENERATION_FILE = '.kinotic/last-generation'

/**
 * Detects changed files within entity paths using git.
 * Stores the commit hash of the last successful generation to determine
 * which files have changed since then.
 */
export class GitChangeDetector {

    private readonly logger: Logger
    private readonly git: SimpleGit

    constructor(logger: Logger) {
        this.logger = logger
        this.git = simpleGit()
    }

    /**
     * Returns the set of absolute file paths that have changed within the given entity paths
     * since the last generation, or null if a full scan is needed (no git, first run, etc.).
     */
    async getChangedEntityFiles(entitiesPaths: string[]): Promise<Set<string> | null> {
        if (!(await this.isGitRepo())) {
            this.logger.log('Not a git repository, performing full scan')
            return null
        }

        const lastHash = this.readLastGenerationHash()
        if (!lastHash) {
            this.logger.log('No previous generation found, performing full scan')
            return null
        }

        // Verify the stored hash still exists in git history
        if (!(await this.isValidCommit(lastHash))) {
            this.logger.log('Previous generation commit no longer exists, performing full scan')
            return null
        }

        const changedFiles = new Set<string>()

        // Files changed between last generation commit and HEAD
        const committedDiff = await this.git.diff(['--name-only', lastHash, 'HEAD'])
        this.addFiles(changedFiles, committedDiff)

        // Unstaged changes in working tree
        const unstagedDiff = await this.git.diff(['--name-only'])
        this.addFiles(changedFiles, unstagedDiff)

        // Staged but not committed changes
        const stagedDiff = await this.git.diff(['--cached', '--name-only'])
        this.addFiles(changedFiles, stagedDiff)

        // New untracked files
        const statusResult = await this.git.status()
        for (const file of statusResult.not_added) {
            changedFiles.add(path.resolve(file))
        }

        // Filter to only .ts files within entity paths
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
    async saveLastGenerationHash(): Promise<void> {
        if (!(await this.isGitRepo())) {
            return
        }

        try {
            const hash = await this.git.revparse('HEAD')
            const filePath = path.resolve(LAST_GENERATION_FILE)
            fs.mkdirSync(path.dirname(filePath), { recursive: true })
            fs.writeFileSync(filePath, hash.trim())
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

    private async isGitRepo(): Promise<boolean> {
        try {
            return await this.git.checkIsRepo()
        } catch {
            return false
        }
    }

    private async isValidCommit(hash: string): Promise<boolean> {
        try {
            await this.git.catFile(['-t', hash])
            return true
        } catch {
            return false
        }
    }

    private addFiles(set: Set<string>, diffOutput: string): void {
        const lines = diffOutput.trim().split('\n').filter(line => line.length > 0)
        for (const line of lines) {
            set.add(path.resolve(line))
        }
    }
}
