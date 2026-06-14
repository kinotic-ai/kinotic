import {Liquid} from 'liquidjs'
import {z} from 'zod'
import type {PropertySchema} from './PropertySchema'
import type {PropertyResolver} from './PropertyResolver'
import type {RenderSpawnOptions} from './RenderSpawnOptions'
import type {SpawnLintResult} from './SpawnLintResult'
import type {SpawnRenderResult} from './SpawnRenderResult'
import type {SpawnTree} from './SpawnTree'
import type {UndeclaredVariable} from './UndeclaredVariable'

// The schemas stay module-private so their types can be inferred (the
// workspace's isolatedDeclarations setting forbids inferred types on exports)
// and spawn.json validation has a single source of truth. PropertySchemaSchema
// is annotated against the public PropertySchema interface so the two cannot
// diverge on a required field.
const PropertySchemaSchema: z.ZodType<PropertySchema> = z.object({
  type: z.enum(['string', 'number', 'integer', 'boolean']).optional(),
  description: z.string().optional(),
  default: z.union([z.string(), z.number(), z.boolean()]).optional(),
  enum: z.array(z.string()).optional(),
})

const SpawnConfigSchema = z.object({
  inherits: z.string().optional(),
  globals: z.record(z.string(), z.unknown()).optional(),
  propertySchema: z.record(z.string(), PropertySchemaSchema).optional(),
})

type SpawnConfig = z.infer<typeof SpawnConfigSchema>

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
 * - a reference to a variable that is neither in the context nor declared in
 *   spawn.json throws rather than rendering empty (strictVariables)
 * - files from derived spawns overwrite same-destination files from inherited
 *   spawns
 */
export class SpawnEngine {

  private engine: Liquid

  constructor() {
    // strictVariables makes a reference to an undeclared variable throw instead of
    // rendering an empty string, so a template that uses a value no one provides
    // fails loudly rather than producing silently-wrong output.
    this.engine = new Liquid({cache: true, strictVariables: true})
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
    // Trees and configs are accumulated derived-first, mirroring the inheritance walk.
    const {trees, configs} = await this.walkInheritance(spawn, options?.loadInherited)

    // Merge base-first so derived spawns override inherited globals and schema entries.
    let globals: Record<string, unknown> = {}
    let propertySchemas: Record<string, PropertySchema> = {}
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
    const sources: Record<string, string> = {}
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
        sources[destination] = source
      }
    }

    return {files, sources, context}
  }

  /**
   * Reports the variables a spawn's templates reference that are declared
   * neither in propertySchema nor in globals, across the full inheritance
   * chain. Variables come from the path of every file plus the contents of
   * {@code .liquid} files; registered filters are not mistaken for variables.
   * A clean spawn has an empty {@code undeclared} list.
   */
  public async lint(spawn: SpawnTree,
                    options?: {loadInherited?: (ref: string) => Promise<SpawnTree>}): Promise<SpawnLintResult> {
    const {trees, configs} = await this.walkInheritance(spawn, options?.loadInherited)

    const declared = new Set<string>()
    for (const config of configs) {
      if (config.globals) {
        Object.keys(config.globals).forEach(key => declared.add(key))
      }
      if (config.propertySchema) {
        Object.keys(config.propertySchema).forEach(key => declared.add(key))
      }
    }

    const usedIn = new Map<string, Set<string>>()
    const record = (name: string, file: string) => {
      let files = usedIn.get(name)
      if (!files) {
        files = new Set<string>()
        usedIn.set(name, files)
      }
      files.add(file)
    }
    // analyze().globals reports only variables not in scope (external inputs),
    // excluding for-loop and assign/capture locals (and forloop), and registered
    // filters are never reported as variables.
    const externalVars = (template: string): string[] =>
      Object.keys(this.engine.parseAndAnalyzeSync(template, undefined, {partials: false}).globals)
    for (const tree of trees) {
      for (const source of Object.keys(tree)) {
        const fileName = source.substring(source.lastIndexOf('/') + 1)
        if (IGNORED_FILE_NAMES.includes(fileName)) {
          continue
        }
        externalVars(source).forEach(name => record(name, source))
        const content = tree[source]
        if (source.endsWith('.liquid') && typeof content === 'string') {
          externalVars(content).forEach(name => record(name, source))
        }
      }
    }

    const undeclared: UndeclaredVariable[] = []
    for (const [name, files] of usedIn) {
      if (!declared.has(name)) {
        undeclared.push({name, files: [...files].sort()})
      }
    }
    undeclared.sort((a, b) => a.name.localeCompare(b.name))
    return {undeclared}
  }

  // Follows the spawn.json inherits chain, accumulating each spawn's tree and
  // parsed config derived-first. Shared by renderSpawn and lint.
  private async walkInheritance(spawn: SpawnTree,
                                loadInherited?: (ref: string) => Promise<SpawnTree>):
      Promise<{trees: SpawnTree[], configs: SpawnConfig[]}> {
    const trees: SpawnTree[] = [spawn]
    const configs: SpawnConfig[] = []

    let currentConfig: SpawnConfig | undefined = this.parseConfig(spawn)
    if (currentConfig) {
      configs.push(currentConfig)
    }

    while (currentConfig?.inherits) {
      if (!loadInherited) {
        throw new Error(`Spawn inherits '${currentConfig.inherits}' but no loadInherited callback was provided`)
      }
      const inherited: SpawnTree = await loadInherited(currentConfig.inherits)
      trees.push(inherited)
      currentConfig = this.parseConfig(inherited)
      if (currentConfig) {
        configs.push(currentConfig)
      }
    }

    return {trees, configs}
  }

  private parseConfig(tree: SpawnTree): SpawnConfig | undefined {
    const raw = tree['spawn.json']
    if (raw === undefined) {
      return undefined
    }
    const text = typeof raw === 'string' ? raw : new TextDecoder().decode(raw)
    return SpawnConfigSchema.parse(JSON.parse(text))
  }

  private async resolveMissingProperties(propertySchemas: Record<string, PropertySchema>,
                                         context: Record<string, unknown>,
                                         resolver?: PropertyResolver): Promise<Record<string, unknown>> {
    const ret: Record<string, unknown> = {...context}

    for (const key in propertySchemas) {
      if (!Object.prototype.hasOwnProperty.call(ret, key)) {
        if (!resolver) {
          throw new Error(`No value provided for required property '${key}'`)
        }

        const schema = propertySchemas[key] as PropertySchema

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
