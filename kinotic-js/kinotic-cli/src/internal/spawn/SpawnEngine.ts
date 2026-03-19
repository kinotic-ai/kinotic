import fs from 'node:fs'
import {Dirent} from 'node:fs'
import fsP from 'node:fs/promises'
import path from 'node:path'
import {Liquid} from 'liquidjs'
import {confirm, input, number, select} from '@inquirer/prompts'
import {SpawnConfig, SpawnConfigSchema, GlobalsType, PropertySchema, PropertySchemaType} from './SpawnConfig'
import {spawnResolver, SpawnResolver} from './SpawnResolver'

function upperFirst(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function camelCase(s: string): string {
  return s
    .replace(/[-_\s]+(.)/g, (_, c: string) => c.toUpperCase())
    .replace(/^(.)/, (_, c: string) => c.toLowerCase())
}

export default class SpawnEngine {

  private engine: Liquid
  private spawnResolver: SpawnResolver

  constructor(resolver: SpawnResolver) {
    this.spawnResolver = resolver
    this.engine = new Liquid({cache: true})
    this.engine.registerFilter('packageToPath', (v: string) => v.replaceAll('.', '/'))
    this.engine.registerFilter('encodePackage', (v: string) => {
      v = v.replaceAll('-', '_')
      v = v.replace(/\.(\d+)/g, '._$1')
      return v
    })
    this.engine.registerFilter('camelCase', (v: string) => camelCase(v))
    this.engine.registerFilter('upperFirst', (v: string) => upperFirst(v))
  }

    /**
     * Renders the specified Spawn
     * A Spawn is a directory that contains templates, an optional spawn.json file, and template parameters in folder and filenames
     *
     * This is done by performing the following
     *
     * - Will recursively walk the spawn copying any files or directories encountered
     * -- If a template file is encountered, it will be parsed and rendered prior to copying
     *
     * @param spawn the name of the spawn to parse and render. This is the name of the directory containing the spawn.json
     * @param destination the target directory where rendered data will be sent
     * @param context the values to be provided to the templates while rendering
     * @return a promise containing all the original values plus any added during rendering
     */
  public async renderSpawn(spawn: string, destination: string, context?: Record<string, unknown>): Promise<Record<string, unknown> | undefined> {
    let contextInternal: Record<string, unknown> | undefined

    if (!fs.existsSync(destination)) {

      const source: string = await this.spawnResolver.resolveSpawn(spawn)
      const sources: string[] = [source]

      const currentConfigPath: string = path.resolve(source, 'spawn.json')
      if (fs.existsSync(currentConfigPath)) {
        const spawns: SpawnConfig[] = []
        let currentConfig = currentConfigPath
        let currentSpawn: SpawnConfig = SpawnConfigSchema.parse(JSON.parse(fs.readFileSync(currentConfig, {encoding: 'utf8'})))
        spawns.push(currentSpawn)

        // follow inheritance and build stack
        while (currentSpawn.inherits) {
          const inheritDir = path.resolve(path.dirname(currentConfig), currentSpawn.inherits)
          currentConfig = path.resolve(inheritDir, 'spawn.json')

          this.logDebug(`Inheriting from ${currentConfig}\n`)

          if (!fs.existsSync(currentConfig)) {
            throw new Error(`Inherited spawn ${currentConfig} does not exist`)
          }
          currentSpawn = SpawnConfigSchema.parse(JSON.parse(fs.readFileSync(currentConfig, {encoding: 'utf8'})))
          spawns.push(currentSpawn)
          sources.push(inheritDir)
        }

        let globals: GlobalsType = {}
        let propertySchemas: PropertySchemaType = {}

        for (const spawnConfig of spawns.reverse()) {
          if (spawnConfig.globals) {
            globals = {...globals, ...spawnConfig.globals}
          }
          if (spawnConfig.propertySchema) {
            propertySchemas = {...propertySchemas, ...spawnConfig.propertySchema}
          }
        }

        contextInternal = {...globals, ...context}
        contextInternal = await this.promptForMissingProperties(propertySchemas, contextInternal)
      }

      await fsP.mkdir(destination, {recursive: true})

      for (const src of sources.reverse()) {
        await this._renderDirectory(src, src, destination, contextInternal)
      }

      return contextInternal

    } else {
      throw new Error(`The target directory ${destination} already exists`)
    }
  }

  private async promptForMissingProperties(propertySchema: PropertySchemaType, context?: Record<string, unknown>): Promise<Record<string, unknown>> {
    const ret: Record<string, unknown> = context ? {...context} : {}

    let hasPrompted = false
    for (const key in propertySchema) {
      if (!Object.prototype.hasOwnProperty.call(ret, key)) {
        if (!hasPrompted) {
          console.log('Please provide the following...\n')
          hasPrompted = true
        }

        const schema: PropertySchema = propertySchema[key]

        let message: string
        if (schema.description?.includes('{{')) {
          message = this.engine.parseAndRenderSync(schema.description, ret)
        } else {
          message = schema.description ?? key
        }

        let defaultValue: unknown
        if (typeof schema.default === 'string' && schema.default.includes('{{')) {
          defaultValue = this.engine.parseAndRenderSync(schema.default, ret)
        } else {
          defaultValue = schema.default
        }

        if (schema.type === 'boolean') {
          ret[key] = await confirm({message, default: typeof defaultValue === 'boolean' ? defaultValue : undefined})
        } else if (schema.enum) {
          ret[key] = await select({
            message,
            choices: schema.enum.map((v: string) => ({value: v})),
            default: defaultValue !== undefined ? String(defaultValue) : undefined
          })
        } else if (schema.type === 'number' || schema.type === 'integer') {
          ret[key] = await number({message, default: typeof defaultValue === 'number' ? defaultValue : undefined})
        } else {
          ret[key] = await input({message, default: typeof defaultValue === 'string' ? defaultValue : undefined})
        }
      }
    }

    return ret
  }

  private async _renderFile(source: string, destination: string, errorIfExists: boolean, context?: Record<string, unknown>): Promise<void> {
    if (destination.includes('{{')) {
      destination = await this.engine.parseAndRender(destination, context ?? {})
    }

    let overwritingFile = false
    if ((errorIfExists) && fs.existsSync(destination)) {
      if (errorIfExists) {
        throw new Error(`The target file ${destination} already exists`)
      }
      overwritingFile = true
    }

    let readStream: NodeJS.ReadableStream

    if (destination.endsWith('.liquid')) {
      destination = destination.substring(0, destination.length - 7)
      readStream = await this.engine.renderFileToNodeStream(source, context ?? {})
    } else {
      readStream = fs.createReadStream(source)
    }

    this.logDebug(`${overwritingFile ? 'Overwriting' : 'Writing'} File\n${source}\nto\n${destination}\n`)

    await fsP.mkdir(path.dirname(destination), {recursive: true})

    const writeStream = fs.createWriteStream(destination)
    readStream.pipe(writeStream)
  }

  private async _renderDirectory(baseFrom: string, from: string, baseTo: string, context?: Record<string, unknown>): Promise<void> {
    const files: Dirent[] = await fsP.readdir(from, {withFileTypes: true})

    for (const file of files) {
      const filePath: string = path.resolve(from, file.name)
      const to: string = filePath.replace(baseFrom, baseTo)

      if (file.isFile()) {
        if (!this.shouldIgnore(file.name)) {
          await this._renderFile(filePath, to, false, context)
        } else {
          this.logDebug(`Skipping File\n${filePath}\n`)
        }
      } else {
        await this._renderDirectory(baseFrom, filePath, baseTo, context)
      }
    }
  }

  private logDebug(message: string): void {
    if (process.env.DEBUG) {
      console.debug(message)
    }
  }

  private shouldIgnore(fileName: string): boolean {
    const filesToSkip = ['.DS_Store', 'spawn.json']
    return filesToSkip.includes(fileName)
  }

}

export const spawnEngine = new SpawnEngine(spawnResolver)
