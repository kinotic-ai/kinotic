import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'url'

const bundledTemplatesRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../templates/')

/**
 * Handles resolving "Spawn"s on the local filesystem
 */
export interface SpawnResolver {

  /**
   * Finds the correct absolute path for the spawn provided.
   *
   * @param spawn the name of desired spawn. Ex: project, library
   * @return a {@link Promise} containing the absolute path or an error if the spawn could not be found
   */
  resolveSpawn(spawn: string): Promise<string>

}


class DefaultSpawnResolver implements SpawnResolver {

  async resolveSpawn(spawn: string): Promise<string> {
    const spawnDir = path.resolve(bundledTemplatesRoot, spawn)
    if (fs.existsSync(spawnDir)) {
      return spawnDir
    }
    throw new Error(`No spawn could be found with the name ${spawn}`)
  }

}

export const spawnResolver = new DefaultSpawnResolver()
