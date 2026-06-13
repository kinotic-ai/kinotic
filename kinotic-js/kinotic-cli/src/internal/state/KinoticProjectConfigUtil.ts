import type { KinoticProjectConfig } from '@kinotic-ai/os-api'
import { loadConfig } from 'c12'
import path from 'path'
import fsPromises from 'fs/promises'
import { Liquid } from 'liquidjs'
import { fileURLToPath } from 'url'

/**
 * Returns the absolute path to the first supported kinotic.config.* file in the .config directory, or undefined if none found.
 */
async function findKinoticConfigFile(): Promise<string | undefined> {
    const configDir = path.resolve(process.cwd(), '.config')
    try {
        const stat = await fsPromises.stat(configDir)
        if (stat.isDirectory()) {
            const files = await fsPromises.readdir(configDir)
            const supported = files.filter(f => f.startsWith('kinotic.config.'))
            if (supported.length > 0) {
                return path.join(configDir, supported[0])
            }
        }
    } catch (e) {
        // Directory does not exist or is not accessible
    }
    return undefined
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
    if (await findKinoticConfigFile()) {
        result = true
    }
    return result
}

export async function loadKinoticProjectConfig(): Promise<KinoticProjectConfig> {
    let result: KinoticProjectConfig | undefined
    const configFile = await findKinoticConfigFile()
    let configDir = path.resolve(process.cwd(), '.config')
    if (configFile) {
        configDir = path.dirname(configFile)
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
        result = config as KinoticProjectConfig
    }

    if (!result) {
        throw new Error('No kinotic project config found and not a legacy project')
    }
    // If name is not set, try to load from package.json in cwd
    if (!result.name || !result.description) {
        try {
            const pkgPath = path.resolve(process.cwd(), 'package.json')
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


