import {NodeSpawnRenderer} from '@kinotic-ai/spawn/node'
import {InquirerPropertyResolver} from './InquirerPropertyResolver'
import {spawnResolver, SpawnResolver} from './SpawnResolver'

/**
 * Renders Spawns bundled with the CLI to the local filesystem, prompting on
 * the terminal for any properties the spawn requires that were not provided in
 * the context. Resolves a spawn by name to its bundled directory and delegates
 * the disk load/render/write (and its directory-traversal guards) to
 * {@link NodeSpawnRenderer}.
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
      context,
      propertyResolver: new InquirerPropertyResolver()
    })
  }

}

export const fileSystemSpawnEngine = new FileSystemSpawnEngine(spawnResolver)
