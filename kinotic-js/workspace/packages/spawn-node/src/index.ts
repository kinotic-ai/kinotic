import fs from 'node:fs'
import fsP from 'node:fs/promises'
import path from 'node:path'
import {SpawnEngine} from '@kinotic-ai/spawn'
import type {PropertyResolver, SpawnTree} from '@kinotic-ai/spawn'

function assertContained(root: string, resolved: string): string {
  const rel = path.relative(root, resolved)
  if (rel.startsWith('..') || path.isAbsolute(rel)) {
    throw new Error(`Path escapes ${root}: ${resolved}`)
  }
  return resolved
}

/**
 * Resolves {@code target} against {@code root} and asserts the result stays at or
 * below {@code root}, so a path can't escape the directory being operated in via
 * {@code ..} (whether authored that way or injected through a property value).
 * Internal {@code ../} that stays within {@code root} is allowed.
 */
export function assertPathWithin(root: string, target: string): string {
  return assertContained(root, path.resolve(root, target))
}

/**
 * Reads every file under {@code dir} into a {@link SpawnTree} keyed by POSIX
 * relative path. Files ending in {@code .liquid} are read as UTF-8 text; all
 * others are read as bytes so binary content survives unrendered.
 */
export async function loadSpawnTree(dir: string): Promise<SpawnTree> {
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
 * Writes a rendered {@link SpawnTree} under {@code destination}, confining every
 * file to {@code destination} so a rendered path can't escape it.
 */
export async function writeSpawnTree(tree: SpawnTree, destination: string): Promise<void> {
  await fsP.mkdir(destination, {recursive: true})
  for (const [treePath, content] of Object.entries(tree)) {
    const filePath = assertPathWithin(destination, treePath)
    await fsP.mkdir(path.dirname(filePath), {recursive: true})
    await fsP.writeFile(filePath, content)
  }
}

/**
 * Options for {@link NodeSpawnRenderer#render}.
 */
export interface NodeRenderOptions {

  /** Values made available to the templates. */
  context?: Record<string, unknown>

  /**
   * Supplies values for propertySchema entries missing from the context. With no
   * resolver, a missing required property fails the render.
   */
  propertyResolver?: PropertyResolver
}

/**
 * Renders a Spawn from a directory on disk to another directory, using the
 * host-agnostic {@link SpawnEngine} for the rendering and confining all reads
 * (inheritance) to the source and all writes to the destination. This is the
 * reusable filesystem adapter so node/bun callers don't reimplement the disk
 * load/write or the directory-traversal guards.
 */
export class NodeSpawnRenderer {

  private engine: SpawnEngine = new SpawnEngine()

  /**
   * Renders the spawn at {@code spawnDir} into {@code destination}, which must not
   * exist yet, and returns the full context used.
   *
   * @throws Error when {@code destination} already exists, an inheritance ref or a
   *         rendered path escapes its root, or a required property has no value
   */
  public async render(spawnDir: string, destination: string, options?: NodeRenderOptions): Promise<Record<string, unknown>> {
    if (fs.existsSync(destination)) {
      throw new Error(`The target directory ${destination} already exists`)
    }

    // Inheritance chains are linear; each ref resolves against the directory of the
    // spawn that declared it but must stay within the original spawn root.
    let currentDir = spawnDir
    const result = await this.engine.renderSpawn(await loadSpawnTree(spawnDir), {
      context: options?.context,
      propertyResolver: options?.propertyResolver,
      loadInherited: async (ref: string) => {
        currentDir = assertContained(spawnDir, path.resolve(currentDir, ref))
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
