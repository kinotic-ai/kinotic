import {ConnectionInfo, IWebSocket, Kinotic} from '@kinotic-ai/core'
import {C3Type, FunctionDefinition, ObjectC3Type} from '@kinotic-ai/idl'
import {confirm} from '@inquirer/prompts'
import fs from 'fs'
import fsPromises from 'fs/promises'
import open from 'open'
import pTimeout from 'p-timeout'
import path from 'path'
import {IndentationText, Node, Project} from 'ts-morph'
import {WebSocket} from 'ws'
import {createConversionContext} from './converter/IConversionContext'
import {TypescriptConversionState} from './converter/typescript/TypescriptConversionState'
import {TypescriptConverterStrategy} from './converter/typescript/TypescriptConverterStrategy'
import {createStateManager} from './state/IStateManager'
import {Logger} from './Logger'

export type GeneratedServiceInfo = {
    entityServiceName: string
    namedQueries: FunctionDefinition[]
}

function isEmpty(value: any): boolean {
    if (value === null || value === undefined) {
        return true;
    }

    if (Array.isArray(value)) {
        return value.every(isEmpty);
    }
    else if (typeof (value) === 'object') {
        return Object.values(value).every(isEmpty);
    }

    return false;
}

export function jsonStringifyReplacer(key: any, value: any) {
    return isEmpty(value)
           ? undefined
           : value;
}

/** OAuth 2.0 token-pair returned by the device-authorization endpoints. */
interface DeviceTokens {
    access_token: string
    refresh_token: string
}

/** Resolved gateway endpoints for a server url — REST and STOMP share the gateway host/port. */
interface ServerTarget {
    host: string
    port: number
    useSSL: boolean
    restBaseUrl: string
    wsUrl: string
}

/** State key the rotating refresh token is persisted under, keyed by server url. */
const CREDENTIALS_KEY = 'kinotic-credentials'

/**
 * Connects {@link Kinotic} to the server, authenticating via the OAuth 2.0 Device
 * Authorization Grant (RFC 8628). A previously stored refresh token is reused when valid;
 * otherwise the user is walked through a browser login. The rotated refresh token is
 * persisted so subsequent runs are non-interactive.
 *
 * @param server the server to connect to
 * @param configDir directory the rotating refresh token is persisted in
 * @param logger the logger to use
 * @return true if the connection was established
 */
export async function connectAndUpgradeSession(server: string,
                                               configDir: string,
                                               logger: Logger): Promise<boolean> {
    try {
        const target = parseServer(server)
        if (target === null) {
            logger.log('Invalid server URL, only http and https are supported')
            return false
        }

        let tokens = await tryStoredRefreshToken(target.restBaseUrl, configDir, server, logger)
        if (tokens === null) {
            tokens = await deviceLogin(target.restBaseUrl, logger)
        }
        if (tokens === null) {
            return false
        }
        await saveRefreshToken(configDir, server, tokens.refresh_token)

        const accessToken = tokens.access_token
        const connectionInfo = new ConnectionInfo()
        connectionInfo.host = target.host
        connectionInfo.port = target.port
        connectionInfo.useSSL = target.useSSL
        // The CLI is a Node client, so it attaches the access token as a WebSocket upgrade
        // header rather than relying on a browser session cookie.
        connectionInfo.webSocketFactory = () => new WebSocket(target.wsUrl, {
            headers: {Authorization: 'Bearer ' + accessToken}
        }) as unknown as IWebSocket

        await pTimeout(Kinotic.connect(connectionInfo), {
            milliseconds: 60000,
            message: 'Connection timeout trying to connect to the Kinotic Server'
        })
        logger.log('Authenticated successfully\n')
        return true
    } catch (e) {
        logger.log('Could not connect to the Kinotic Server. Please check the server is running and the URL is correct.', e)
        return false
    }
}

/** Parses the server url into the host/port the gateway serves both REST and STOMP on. */
function parseServer(server: string): ServerTarget | null {
    const url = new URL(server)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return null
    }
    const useSSL = url.protocol === 'https:'
    // Locally the server url often points at the static web port; the gateway (REST + STOMP)
    // always listens on 58503, so the port is overridden.
    let port: number
    if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
        port = 58503
    } else if (url.port) {
        port = Number(url.port)
    } else {
        port = useSSL ? 443 : 58503
    }
    return {
        host: url.hostname,
        port,
        useSSL,
        restBaseUrl: (useSSL ? 'https' : 'http') + '://' + url.hostname + ':' + port,
        wsUrl: (useSSL ? 'wss' : 'ws') + '://' + url.hostname + ':' + port + '/v1'
    }
}

/** Runs the RFC 8628 device-authorization flow: start, browser approval, then poll for tokens. */
async function deviceLogin(restBaseUrl: string, logger: Logger): Promise<DeviceTokens | null> {
    const startRes = await fetch(restBaseUrl + '/api/login/device/start', {method: 'POST'})
    if (!startRes.ok) {
        logger.log('Could not start device authorization with the Kinotic Server.')
        return null
    }
    const start = await startRes.json() as {
        device_code: string
        user_code: string
        verification_uri_complete: string
        expires_in: number
        interval: number
    }

    logger.log('Authenticate your account at:')
    logger.log(start.verification_uri_complete)
    logger.log(`Your code is: ${start.user_code}`)

    const answer = await confirm({message: 'Open in browser?', default: true})
    if (answer) {
        await open(start.verification_uri_complete)
    }

    const deadline = Date.now() + start.expires_in * 1000
    let intervalMs = Math.max(start.interval, 1) * 1000
    while (Date.now() < deadline) {
        await delay(intervalMs)
        const tokenRes = await fetch(restBaseUrl + '/api/login/device/token', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({device_code: start.device_code})
        })
        if (tokenRes.ok) {
            return await tokenRes.json() as DeviceTokens
        }
        const error = await readErrorCode(tokenRes)
        if (error === 'authorization_pending') {
            continue
        } else if (error === 'slow_down') {
            intervalMs += 5000
        } else {
            logger.log(`Device authorization failed: ${error}`)
            return null
        }
    }
    logger.log('Device authorization timed out before it was approved.')
    return null
}

/** Reuses a stored refresh token for {@code server}; null when there is none or it is rejected. */
async function tryStoredRefreshToken(restBaseUrl: string,
                                     configDir: string,
                                     server: string,
                                     logger: Logger): Promise<DeviceTokens | null> {
    const refreshToken = await loadRefreshToken(configDir, server)
    if (refreshToken === null) {
        return null
    }
    const res = await fetch(restBaseUrl + '/api/login/device/refresh', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({refresh_token: refreshToken})
    })
    if (res.ok) {
        return await res.json() as DeviceTokens
    }
    logger.log('Stored credentials are no longer valid, a new login is required.')
    return null
}

async function loadRefreshToken(configDir: string, server: string): Promise<string | null> {
    const stateManager = createStateManager(configDir)
    if (!(await stateManager.containsState(CREDENTIALS_KEY))) {
        return null
    }
    const credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
    return credentials[server] ?? null
}

async function saveRefreshToken(configDir: string, server: string, refreshToken: string): Promise<void> {
    const stateManager = createStateManager(configDir)
    let credentials: Record<string, string> = {}
    if (await stateManager.containsState(CREDENTIALS_KEY)) {
        credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
    }
    credentials[server] = refreshToken
    await stateManager.save(CREDENTIALS_KEY, credentials)
}

async function readErrorCode(res: Response): Promise<string> {
    try {
        const body = await res.json() as {error?: string}
        return body.error ?? 'unknown_error'
    } catch {
        return 'unknown_error'
    }
}

function delay(ms: number): Promise<void> {
    return new Promise<void>(resolve => setTimeout(resolve, ms))
}

export type EntityInfo = {
    exportedFromFile: string,
    defaultExport: boolean,
    entity: ObjectC3Type
    multiTenantSelectionEnabled: boolean
}

export type ConversionConfiguration = {
    application: string,
    entitiesPath: string,
    verbose: boolean,
    logger: Logger,
}

function getEntityDecoratorIfExists(node: Node){
    if(Node.isClassDeclaration(node)){
        return node.getDecorator('Entity')
    }
}

export function pathToTsGlobPath(path: string): string{
    return path.endsWith('.ts') ? path : (path.endsWith('/') ? path + '*.ts' : path + '/*.ts')
}

export function createTsMorphProject(): Project {
    const tsConfigFilePath = path.resolve('tsconfig.json')
    if(!fs.existsSync(tsConfigFilePath)){
        throw new Error(`No tsconfig.json found in working directory: ${process.cwd()}`)
    }
    return new Project({
       tsConfigFilePath: tsConfigFilePath,
       manipulationSettings: {
           indentationText: IndentationText.TwoSpaces
       }

       // compilerOptions: {
       //     target: ScriptTarget.ES2020,
       //     useDefineForClassFields: true,
       //     module: ModuleKind.ES2020,
       //     lib: ["ES2020"],
       //     skipLibCheck: true,
       //     downlevelIteration: true,
       //     emitDecoratorMetadata: true,
       //     experimentalDecorators: true,
       //     esModuleInterop: true,
       //     moduleResolution: ModuleResolutionKind.NodeNext,
       //     resolveJsonModule: true,
       //     isolatedModules: true,
       //     noEmit: true,
       // }
    })
}

/**
 * Converts all entities found in the given path configuration.
 * @param config the conversion configuration
 * @param changedFiles optional set of absolute file paths to limit processing to. If null, all files are processed.
 */
export function convertAllEntities(config: ConversionConfiguration, changedFiles?: Set<string> | null): EntityInfo[]{
    const entities: EntityInfo[] = []

    const project = createTsMorphProject()

    if(config.verbose) {
        project.enableLogging(true)
    }
    let absEntitiesPath = path.resolve(config.entitiesPath)
    if(!absEntitiesPath.endsWith('.ts') && !absEntitiesPath.endsWith(path.sep)){
        absEntitiesPath = absEntitiesPath + path.sep
    }

    project.addSourceFilesAtPaths(pathToTsGlobPath(config.entitiesPath))

    const sourceFiles = project.getSourceFiles()
    for (const sourceFile of sourceFiles) {

        const absSourcePath = path.resolve(sourceFile.getFilePath())

        // make sure this file is in our configured paths and not just introduced by the ts-config
        if(absSourcePath.startsWith(absEntitiesPath)) {

            // Skip files that haven't changed if incremental mode is active
            if(changedFiles && !changedFiles.has(absSourcePath)) {
                continue
            }

            const conversionContext =
                      createConversionContext(new TypescriptConverterStrategy(new TypescriptConversionState(config.application),
                                                                              config.logger))

            const exportedDeclarations = sourceFile.getExportedDeclarations()
            exportedDeclarations.forEach((exportedDeclarationEntries, name) => {
                exportedDeclarationEntries.forEach((exportedDeclaration) => {
                    if (Node.isClassDeclaration(exportedDeclaration) || Node.isInterfaceDeclaration(exportedDeclaration)) {
                        const declaration = exportedDeclaration

                        // If the Entity is decorated with @Entity or has an EntityConfiguration we convert it
                        const decorator = getEntityDecoratorIfExists(exportedDeclaration)
                        if (decorator) {

                            let c3Type: C3Type | null = null
                            try {
                                conversionContext.state().multiTenantSelectionEnabled = false
                                c3Type = conversionContext.convert(declaration.getType())
                            } catch (e) {
                            } // We ignore this error since the converter will print any errors

                            if (c3Type != null) {

                                if (c3Type instanceof ObjectC3Type) {

                                    entities.push({
                                                      exportedFromFile: declaration.getSourceFile().getFilePath(),
                                                      defaultExport: declaration.isDefaultExport(),
                                                      entity: c3Type,
                                                      multiTenantSelectionEnabled: conversionContext.state().multiTenantSelectionEnabled
                                                  })
                                } else {
                                    throw new Error(`Could not convert ${name} to a C3Type`)
                                }
                            } else {
                                throw new Error(`Could not convert ${name} to a C3Type`)
                            }
                        }
                    }
                })
            })
        }
    }
    return entities
}

/**
 * Will return the relative path from the 'from' path to the 'to' path
 * @param from path to start from
 * @param to path to end at
 * @param fileExtensionForImports this is the file extension to append to the end of the relative path
 */
export function getRelativeImportPath(from: string, to: string, fileExtensionForImports: string = '') {
    if(!from){
        throw new Error('from path is required')
    }
    if(!to){
        throw new Error('to path is required')
    }
    const fromDir = path.dirname(from);
    let relativePath = path.relative(fromDir, to)

    // Normalize path separators to forward slashes for import statements
    // This is required because import statements always use forward slashes,
    // even on Windows, but path.relative() returns platform-specific separators
    relativePath = relativePath.replace(/\\/g, '/')

    // Make sure path starts with './' or '../'
    if (!relativePath.startsWith('../') && !relativePath.startsWith('./')) {
        relativePath = `./${relativePath}`
    }

    // Remove '.ts' extension
    relativePath = relativePath.replace(/\.ts$/, '')
    return relativePath + fileExtensionForImports;
}

/**
 * Will return the name of the node module if the path is within a node module or null if not
 * @param nodeModulePath to check
 */
export function tryGetNodeModuleName(nodeModulePath: string): string | null {
    let ret: string | null = null
    if(nodeModulePath.includes('node_modules')) {
        const nodeModuleIdx = nodeModulePath.indexOf('node_modules/')
        const partBeforeNodeModules = nodeModulePath.slice(0, nodeModuleIdx+13)
        const partAfterNodeModules = nodeModulePath.slice(nodeModuleIdx+13)
        const parts = partAfterNodeModules.split('/')
        let previousPartPath = ''
        for (let part of parts) {
            const packagePath = path.resolve(partBeforeNodeModules, previousPartPath, part, 'package.json')
            if(fs.existsSync(packagePath)){
                const packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf8'))
                ret = packageJson.name
                break
            }else{
                previousPartPath = part + '/'
            }
        }
    }
    return ret
}

/**
 * Saves the C3Type to the local filesystem
 * @param savePath to save the entities to
 * @param entity to save
 * @param logger to log to if desired, if null nothing will be logged
 */
export async function writeEntityJsonToFilesystem(savePath: string, entity: ObjectC3Type, logger?: Logger): Promise<void> {
    const json = JSON.stringify(entity, jsonStringifyReplacer, 2)
    if (json && json.length > 0) {
        const outputPath = path.resolve(savePath, 'generated', 'entity-definitions', `${entity.namespace}.${entity.name}.json`)
        await fsPromises.mkdir(path.dirname(outputPath), {recursive: true})
        await fsPromises.writeFile(outputPath, json)
        if (logger) {
            logger.log(`Wrote ${entity.namespace}.${entity.name} to ${outputPath}`)
        }
    }
}

/**
 * Save the C3Type(s) to the local filesystem
 * @param savePath to save the entities to
 * @param entities to save
 * @param logger to log to if desired, if null nothing will be logged
 */
export async function writeEntitiesJsonToFilesystem(savePath: string, entities: ObjectC3Type[], logger?: Logger): Promise<void> {
    for(const entity of entities){
        await writeEntityJsonToFilesystem(savePath, entity, logger)
    }
}

export async function writeGeneratedServiceInfoToFilesystem(savePath: string, info: GeneratedServiceInfo, logger?: Logger): Promise<void> {
    const json = JSON.stringify(info, jsonStringifyReplacer, 2)
    if (json && json.length > 0) {
        const outputPath = path.resolve(savePath, 'generated', 'query-definitions', `${info.entityServiceName}.json`)
        await fsPromises.mkdir(path.dirname(outputPath), {recursive: true})
        await fsPromises.writeFile(outputPath, json)
        if (logger) {
            logger.log(`Wrote ${info.entityServiceName} named queries to ${outputPath}`)
        }
    }
}
