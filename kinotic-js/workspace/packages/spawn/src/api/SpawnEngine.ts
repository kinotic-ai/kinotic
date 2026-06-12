import {Liquid} from 'liquidjs'
import {SpawnConfigSchema} from './SpawnConfig'
import type {GlobalsType, PropertySchemaType, SpawnConfig} from './SpawnConfig'
import type {PropertyResolver} from './PropertyResolver'
import type {RenderSpawnOptions} from './RenderSpawnOptions'
import type {SpawnRenderResult} from './SpawnRenderResult'
import type {SpawnTree} from './SpawnTree'

const IGNORED_FILE_NAMES: string[] = ['spawn.json', '.DS_Store']

function upperFirst(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function camelCase(s: string): string {
  return s
    .replace(/[-_\s]+(.)/g, (_, c: string) => c.toUpperCase())
    .replace(/^(.)/, (_, c: string) => c.toLowerCase())
}

/**
 * Renders a Spawn: a file tree containing liquid templates, an optional
 * spawn.json, and liquid expressions in file paths. The engine is
 * host-agnostic — it operates on in-memory {@link SpawnTree}s and performs no
 * IO, so the same implementation runs in Node and embedded in the JVM.
 *
 * Rendering applies the following rules:
 *
 * - spawn.json declares globals, a propertySchema, and an optional inherited
 *   spawn; inheritance chains are followed via
 *   {@link RenderSpawnOptions#loadInherited}
 * - globals and propertySchema entries from derived spawns override inherited
 *   ones; values supplied in the context override globals
 * - propertySchema entries missing from the context are obtained from the
 *   {@link PropertyResolver}, or fail the render when no resolver is given
 * - paths containing liquid expressions are rendered; files ending in
 *   {@code .liquid} have their content rendered and the suffix stripped
 * - files from derived spawns overwrite same-destination files from inherited
 *   spawns
 */
export class SpawnEngine {

  private engine: Liquid

  constructor() {
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
   * Renders the given spawn and returns the rendered files along with the full
   * context used, including values added by the property resolver.
   */
  public async renderSpawn(spawn: SpawnTree, options?: RenderSpawnOptions): Promise<SpawnRenderResult> {
    // Trees are accumulated derived-first, mirroring the inheritance walk.
    const trees: SpawnTree[] = [spawn]
    const configs: SpawnConfig[] = []

    let currentConfig: SpawnConfig | undefined = this.parseConfig(spawn)
    if (currentConfig) {
      configs.push(currentConfig)
    }

    while (currentConfig?.inherits) {
      if (!options?.loadInherited) {
        throw new Error(`Spawn inherits '${currentConfig.inherits}' but no loadInherited callback was provided`)
      }
      const inherited: SpawnTree = await options.loadInherited(currentConfig.inherits)
      trees.push(inherited)
      currentConfig = this.parseConfig(inherited)
      if (currentConfig) {
        configs.push(currentConfig)
      }
    }

    // Merge base-first so derived spawns override inherited globals and schema entries.
    let globals: GlobalsType = {}
    let propertySchemas: PropertySchemaType = {}
    for (const config of [...configs].reverse()) {
      if (config.globals) {
        globals = {...globals, ...config.globals}
      }
      if (config.propertySchema) {
        propertySchemas = {...propertySchemas, ...config.propertySchema}
      }
    }

    let context: Record<string, unknown> = {...globals, ...options?.context}
    context = await this.resolveMissingProperties(propertySchemas, context, options?.propertyResolver)

    const files: SpawnTree = {}
    // Render base-first so derived spawns overwrite same-destination files.
    for (const tree of [...trees].reverse()) {
      for (const source of Object.keys(tree).sort()) {
        const fileName = source.substring(source.lastIndexOf('/') + 1)
        if (IGNORED_FILE_NAMES.includes(fileName)) {
          continue
        }

        let destination = source
        if (destination.includes('{{')) {
          destination = await this.engine.parseAndRender(destination, context)
        }

        let content: string | Uint8Array = tree[source] as string | Uint8Array
        if (destination.endsWith('.liquid')) {
          destination = destination.substring(0, destination.length - 7)
          if (typeof content !== 'string') {
            throw new Error(`Template ${source} must contain text content`)
          }
          content = await this.engine.parseAndRender(content, context)
        }

        files[destination] = content
      }
    }

    return {files, context}
  }

  private parseConfig(tree: SpawnTree): SpawnConfig | undefined {
    const raw = tree['spawn.json']
    if (raw === undefined) {
      return undefined
    }
    const text = typeof raw === 'string' ? raw : new TextDecoder().decode(raw)
    return SpawnConfigSchema.parse(JSON.parse(text))
  }

  private async resolveMissingProperties(propertySchemas: PropertySchemaType,
                                         context: Record<string, unknown>,
                                         resolver?: PropertyResolver): Promise<Record<string, unknown>> {
    const ret: Record<string, unknown> = {...context}

    for (const key in propertySchemas) {
      if (!Object.prototype.hasOwnProperty.call(ret, key)) {
        if (!resolver) {
          throw new Error(`No value provided for required property '${key}'`)
        }

        const schema = propertySchemas[key] as PropertySchemaType[string]

        let message: string
        if (schema.description?.includes('{{')) {
          message = this.engine.parseAndRenderSync(schema.description, ret)
        } else {
          message = schema.description ?? key
        }

        let defaultValue: unknown = schema.default
        if (typeof schema.default === 'string' && schema.default.includes('{{')) {
          defaultValue = this.engine.parseAndRenderSync(schema.default, ret)
        }

        ret[key] = await resolver.resolve(key, schema, message, defaultValue)
      }
    }

    return ret
  }

}
