import fs from 'node:fs'
import fsP from 'node:fs/promises'
import path from 'node:path'
import {SpawnEngine} from '@kinotic-ai/spawn'
import type {SpawnTree} from '@kinotic-ai/spawn'
import {InquirerPropertyResolver} from './InquirerPropertyResolver'
import {spawnResolver, SpawnResolver} from './SpawnResolver'

async function loadSpawnTree(dir: string): Promise<SpawnTree> {
  const tree: SpawnTree = {}
  const entries = await fsP.readdir(dir, {recursive: true, withFileTypes: true})
  for (const entry of entries) {
    if (!entry.isFile()) {
      continue
    }
    const filePath = path.join(entry.parentPath, entry.name)
    const treePath = path.relative(dir, filePath).split(path.sep).join('/')
    if (treePath.endsWith('.liquid')) {
      tree[treePath] = await fsP.readFile(filePath, {encoding: 'utf8'})
    } else {
      tree[treePath] = await fsP.readFile(filePath)
    }
  }
  return tree
}

/**
 * Asserts {@code resolved} is at or below {@code root}, so a template can't escape
 * the directory we're operating in via {@code ..} (whether authored in a path or
 * injected through a property value). Relies on the resolver having already
 * canonicalized {@code ..}; {@code ../} that stays within {@code root} is allowed.
 */
function assertContained(root: string, resolved: string): string {
  const rel = path.relative(root, resolved)
  if (rel.startsWith('..') || path.isAbsolute(rel)) {
    throw new Error(`Path escapes ${root}: ${resolved}`)
  }
  return resolved
}

async function writeSpawnTree(tree: SpawnTree, destination: string): Promise<void> {
  await fsP.mkdir(destination, {recursive: true})
  for (const [treePath, content] of Object.entries(tree)) {
    const filePath = assertContained(destination, path.resolve(destination, treePath))
    await fsP.mkdir(path.dirname(filePath), {recursive: true})
    await fsP.writeFile(filePath, content)
  }
}

/**
 * Renders Spawns bundled with the CLI to the local filesystem, prompting on
 * the terminal for any properties the spawn requires that were not provided
 * in the context.
 */
export class FileSystemSpawnEngine {

  private engine: SpawnEngine = new SpawnEngine()
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
    if (fs.existsSync(destination)) {
      throw new Error(`The target directory ${destination} already exists`)
    }

    const source: string = await this.spawnResolver.resolveSpawn(spawn)

    // Inheritance chains are linear, so each inherits ref resolves against the
    // directory of the spawn that declared it — but must stay within the root
    // spawn so a template can't pull in spawn.json from outside the tree.
    let currentDir = source
    const result = await this.engine.renderSpawn(await loadSpawnTree(source), {
      context,
      propertyResolver: new InquirerPropertyResolver(),
      loadInherited: async (ref: string) => {
        currentDir = assertContained(source, path.resolve(currentDir, ref))
        if (!fs.existsSync(path.resolve(currentDir, 'spawn.json'))) {
          throw new Error(`Inherited spawn ${path.resolve(currentDir, 'spawn.json')} does not exist`)
        }
        return loadSpawnTree(currentDir)
      }
    })

    await writeSpawnTree(result.files, destination)

    return result.context
  }

}

export const fileSystemSpawnEngine = new FileSystemSpawnEngine(spawnResolver)
