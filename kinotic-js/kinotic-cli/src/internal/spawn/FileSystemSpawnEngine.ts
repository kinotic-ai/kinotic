import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {NodeSpawnRenderer} from '@kinotic-ai/spawn/node'
import {InquirerPropertyResolver} from './InquirerPropertyResolver'
import {spawnResolver, SpawnResolver} from './SpawnResolver'

const cliPackageJson = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../package.json')

/**
 * Turns an npm package name into the spawn global a template pins it through,
 * dropping the scope and a kinotic- prefix on the name: @kinotic-ai/os-api
 * becomes kinoticOsApiVersion and @kinotic-ai/kinotic-cli becomes
 * kinoticCliVersion.
 */
function globalNameFor(packageName: string): string {
  const unscoped: string = packageName.replace(/^@[^/]+\//, '').replace(/^kinotic-/, '')
  const camelCased: string = unscoped.replace(/-(.)/g, (_, c: string) => c.toUpperCase())
  return `kinotic${camelCased.charAt(0).toUpperCase()}${camelCased.slice(1)}Version`
}

/**
 * The @kinotic-ai version ranges every rendered spawn is given, taken from this
 * CLI's own dependency ranges so a generated project resolves the packages the
 * CLI was built against, and follows along when those ranges are bumped.
 */
function npmPackageVersions(): Record<string, string> {
  const pkg = JSON.parse(fs.readFileSync(cliPackageJson, 'utf8')) as
      {name: string, version: string, dependencies: Record<string, string>}
  const ret: Record<string, string> = {[globalNameFor(pkg.name)]: `^${pkg.version}`}
  for (const [name, range] of Object.entries(pkg.dependencies)) {
    if (name.startsWith('@kinotic-ai/')) {
      ret[globalNameFor(name)] = range
    }
  }
  return ret
}

const versions: Record<string, string> = npmPackageVersions()

/**
 * Renders Spawns bundled with the CLI to the local filesystem, prompting on
 * the terminal for any properties the spawn requires that were not provided in
 * the context. Resolves a spawn by name to its bundled directory and delegates
 * the disk load/render/write (and its directory-traversal guards) to
 * {@link NodeSpawnRenderer}.
 * <p>
 * Every render is given a version range for each @kinotic-ai package this CLI
 * depends on, keyed by the spawn global a template pins it through
 * ({@code kinoticCoreVersion}, {@code kinoticCliVersion}, ...).
 */
export class FileSystemSpawnEngine {

  private renderer: NodeSpawnRenderer = new NodeSpawnRenderer()
  private spawnResolver: SpawnResolver

  constructor(resolver: SpawnResolver) {
    this.spawnResolver = resolver
  }

  /**
   * Renders the named spawn into {@code destination}, which must not exist yet.
   *
   * @param spawn the name of the spawn to render. This is the name of the directory containing the spawn.json
   * @param destination the target directory where rendered files will be written
   * @param context the values to be provided to the templates while rendering
   * @return a promise containing all the original values plus any added during rendering
   */
  public async renderSpawn(spawn: string, destination: string, context?: Record<string, unknown>): Promise<Record<string, unknown>> {
    const source: string = await this.spawnResolver.resolveSpawn(spawn)
    return this.renderer.render(source, destination, {
      // The versions seed the context, so a spawn that also declares them as globals
      // renders with this CLI's ranges instead of its own defaults.
      context: {...versions, ...context},
      propertyResolver: new InquirerPropertyResolver()
    })
  }

}

export const fileSystemSpawnEngine = new FileSystemSpawnEngine(spawnResolver)
