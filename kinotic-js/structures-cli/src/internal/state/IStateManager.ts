import fsPromises from 'fs/promises'
import fs from 'fs'
import path from 'path'

export interface IStateManager {

    /**
     * Checks if the state manager contains a state for the given key
     * @param key the key to check for
     */
    containsState(key: string): Promise<boolean>

    /**
     * Saves the given state to the state manager
     * @param key the key to save the state under
     * @param state the state to save
     */
    save<T>(key: string, state: T): Promise<void>

    /**
     * Loads the state for the given key
     * @param key the key to load the state for
     */
    load<T>(key: string): Promise<T>

}

class DefaultStateManager implements IStateManager {

    private readonly dataDir: string

    constructor(dataDir: string) {
        this.dataDir = dataDir
    }

    async containsState(key: string): Promise<boolean> {
        const filePath = path.resolve(this.dataDir, `${key}.json`)
        if(fs.existsSync(filePath)) {
            return Promise.resolve(true)
        }else {
            return Promise.resolve(false)
        }
    }

    async load<T>(key: string): Promise<T> {
        const filePath = path.resolve(this.dataDir, `${key}.json`)
        if(fs.existsSync(filePath)){
            const data = await fsPromises.readFile(filePath, {encoding: 'utf-8'})
            try {
                return JSON.parse(data) as T
            } catch (e) {
                return Promise.reject('Failed to parse JSON for filePath: ' + filePath + '\n' + e)
            }
        }else{
            return Promise.reject('State not found for key: ' + key)
        }
    }

    async save<T>(key: string, state: T): Promise<void> {
        const filePath = path.resolve(this.dataDir, `${key}.json`)
        await fsPromises.mkdir(path.dirname(filePath), {recursive: true})
        return fsPromises.writeFile(filePath, JSON.stringify(state, null, 2))
    }

}

export function createStateManager(dataDir: string): IStateManager {
    return new DefaultStateManager(dataDir)
}
