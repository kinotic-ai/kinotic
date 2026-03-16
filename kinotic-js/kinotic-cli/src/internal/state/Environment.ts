import { createStateManager } from './IStateManager.js'

const ENVIRONMENT_KEY = 'structures-environment'

export class ServerConfiguration {
    name!: string
    url!: string
}

export class Environment {

    public servers: ServerConfiguration[] = []

    public defaultServer!: ServerConfiguration

    public hasServer(server: string): boolean {
        return this.findServer(server) !== null
    }

    public findServer(server: string): ServerConfiguration | null {
        let ret: ServerConfiguration | null = null
        for(const serverConfig of this.servers){
            if(serverConfig.name === server
                || serverConfig.url === server){
                ret = serverConfig
                break
            }
        }
        return ret
    }
}

export async function loadEnvironment(dataDir: string): Promise<Environment>{
    const stateManager = createStateManager(dataDir)
    if(await stateManager.containsState(ENVIRONMENT_KEY)){
        const jsonEnv = await stateManager.load<Environment>(ENVIRONMENT_KEY)
        const ret = new Environment() // we do this to ensure that the object has the correct prototype
        if(jsonEnv.servers){
            ret.servers = jsonEnv.servers
        }
        ret.defaultServer = jsonEnv.defaultServer
        return ret
    }else {
        return new Environment()
    }
}

export async function saveEnvironment(dataDir: string, environment: Environment): Promise<void> {
    const stateManager = createStateManager(dataDir)
    await stateManager.save(ENVIRONMENT_KEY, environment)
}

export async function resolveServer(dataDir: string, server?: string | null): Promise<ServerConfiguration>{
    const environment = await loadEnvironment(dataDir)
    let ret: ServerConfiguration | null
    if(server){
        ret = environment.findServer(server)
        if(ret === null){
            ret = new ServerConfiguration()
            ret.name = server
            ret.url = server
            environment.servers.push(ret)
            environment.defaultServer = ret
            await saveEnvironment(dataDir, environment)
        }
    } else {
        if(!environment.defaultServer){
            throw new Error("No Default Server configured. Please specify a Server or set a Default Server.")
        }
        ret = environment.defaultServer
    }
    return ret
}
