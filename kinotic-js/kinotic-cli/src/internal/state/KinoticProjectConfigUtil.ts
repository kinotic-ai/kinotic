import type { KinoticProjectConfig } from '@kinotic-ai/os-api'
import { loadConfig } from 'c12'
import path from 'path'
import fs from 'fs'
import fsPromises from 'fs/promises'
import { Liquid } from 'liquidjs'
import { fileURLToPath } from 'url'

/**
 * Returns the absolute path to the project's .config directory: the nearest one at or above
 * the working directory holding a kinotic.config.* file, so CLI commands work from any
 * directory inside the project. Falls back to the working directory's .config when no
 * project config exists yet (a project being initialized).
 */
export function resolveKinoticConfigDir(): string {
    let ret: string
    const configFile = findKinoticConfigFile()
    if (configFile) {
        ret = path.dirname(configFile)
    } else {
        ret = path.resolve(process.cwd(), '.config')
    }
    return ret
}

/**
 * Changes the working directory to the project root — the parent of the resolved .config
 * directory — so the cwd-relative paths commands rely on (tsconfig.json, entitiesPaths,
 * ./migrations) resolve against the project no matter which subdirectory the command ran
 * from. Call after confirming {@link isKinoticProject}.
 */
export function chdirToProjectRoot(): void {
    process.chdir(path.dirname(resolveKinoticConfigDir()))
}

/**
 * Returns the absolute path to the first supported kinotic.config.* file in the nearest
 * .config directory at or above the working directory, or undefined when none is found.
 */
function findKinoticConfigFile(): string | undefined {
    let ret: string | undefined
    let dir = process.cwd()
    for (;;) {
        ret = findKinoticConfigFileIn(path.join(dir, '.config'))
        const parent = path.dirname(dir)
        if (ret !== undefined || parent === dir) {
            break
        }
        dir = parent
    }
    return ret
}

function findKinoticConfigFileIn(configDir: string): string | undefined {
    let ret: string | undefined
    try {
        const supported = fs.readdirSync(configDir).filter(f => f.startsWith('kinotic.config.'))
        if (supported.length > 0) {
            ret = path.join(configDir, supported[0])
        }
    } catch {
        // no .config directory at this level
    }
    return ret
}

/**
 * Helper function to render a value as TypeScript code with proper indentation
 */
function renderValue(value: any, indent: number = 0): string {
    const indentStr = '  '.repeat(indent)
    const nextIndentStr = '  '.repeat(indent + 1)

    if (value === null) {
        return 'null'
    }
    if (value === undefined) {
        return 'undefined'
    }
    if (typeof value === 'boolean') {
        return String(value)
    }
    if (typeof value === 'string') {
        // JSON.stringify produces a valid JS/TS string literal with correct escaping
        // (handles backslashes, quotes, newlines, tabs, etc.)
        return JSON.stringify(value)
    }
    if (typeof value === 'number') {
        return String(value)
    }
    if (Array.isArray(value)) {
        if (value.length === 0) {
            return '[]'
        }
        const items = value.map(item => `${nextIndentStr}${renderValue(item, indent + 1)}`).join(',\n')
        return `[\n${items}\n${indentStr}]`
    }
    if (typeof value === 'object') {
        const entries = Object.entries(value)
            .filter(([key, val]) => val !== undefined) // Only filter out undefined values

        if (entries.length === 0) {
            return '{}'
        }

        const props = entries
            .map(([key, val]) => `${nextIndentStr}${key}: ${renderValue(val, indent + 1)}`)
            .join(',\n')
        return `{\n${props}\n${indentStr}}`
    }
    return 'undefined'
}

/**
 * Saves a KinoticProjectConfig to the .config directory using the appropriate Liquid template.
 * @param config The config object to save
 * @param configDir The directory to save the config file in (usually .config)
 */
export async function saveKinoticProjectConfig(config: KinoticProjectConfig, configDir: string): Promise<void> {
    const engine = new Liquid({
        root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../templates/'),
        extname: '.liquid'
    })

    // Register custom tags for rendering config values
    engine.registerTag('render_value', {
        parse: function(tagToken: any) {
            const args = tagToken.args.trim().split(/\s+/)
            this.value = args[0]
            this.indent = args[1] || '0' // Default indent to 0
        },
        render: function*(ctx: any): Generator<any, string, any> {
            const value = yield (engine as any).evalValue(this.value, ctx)
            const indentLevel = yield (engine as any).evalValue(this.indent, ctx)
            return renderValue(value, indentLevel || 0)
        }
    })
    const outFile = path.join(configDir, 'kinotic.config.ts')
    const templateFile = 'KinoticProjectConfig.ts.liquid'
    const renderContext = { config }
    try {
        await fsPromises.mkdir(configDir)
    } catch (e) {
        // Directory may already exist
    }
    const fileContent = await engine.renderFile(templateFile, renderContext)
    await fsPromises.writeFile(outFile, fileContent)
}


export async function isKinoticProject(): Promise<boolean> {
    let result = false
    if (findKinoticConfigFile()) {
        result = true
    }
    return result
}

export async function loadKinoticProjectConfig(): Promise<KinoticProjectConfig> {
    const configFile = findKinoticConfigFile()
    if (!configFile) {
        throw new Error('No kinotic project config found and not a legacy project')
    }
    const configDir = path.dirname(configFile)
    const { config } = await loadConfig({
        configFile: configFile,
        name: 'kinotic',
        cwd: configDir,
        dotenv: false,
        packageJson: false
    })
    if (!config) {
        throw new Error(`Failed to load config from ${configFile}`)
    }
    const result = config as KinoticProjectConfig
    // If name is not set, try to load from package.json in the project root
    if (!result.name || !result.description) {
        try {
            const pkgPath = path.resolve(configDir, '..', 'package.json')
            const pkgRaw = await fsPromises.readFile(pkgPath, 'utf-8')
            const pkg = JSON.parse(pkgRaw)
            if (!result.name && pkg.name) {
                result.name = pkg.name
            } else {
                throw new Error('No "name" field found in package.json. Please set the name in your KinoticProjectConfig or package.json.')
            }
            if (!result.description && pkg.description) {
                result.description = pkg.description
            }
        } catch (e) {
            throw new Error('Could not determine project name. Please set the name in your KinoticProjectConfig.\nOriginal error: ' + (e instanceof Error ? e.message : String(e)))
        }
    }
    return result
}


