import fs from 'fs'
import path from 'path'
import {FunctionDeclaration, Project} from 'ts-morph'
import {TypescriptExternalProjectConfig} from '@mindignited/structures-api'
import {pathToTsGlobPath} from './Utils.js'

export class UtilFunctionLocator {

    private config: TypescriptExternalProjectConfig
    private project: Project
    private functionCache = new Map<string, FunctionDeclaration>()

    constructor(config: TypescriptExternalProjectConfig, verbose: boolean) {
        this.config = config

        const tsConfigFilePath = path.resolve('tsconfig.json')
        if(!fs.existsSync(tsConfigFilePath)){
            throw new Error(`No tsconfig.json found in working directory: ${process.cwd()}`)
        }
        this.project = new Project({
            tsConfigFilePath: tsConfigFilePath
        })

        if(verbose) {
            this.project.enableLogging(true)
        }

        if(config.utilFunctionsPaths) {
            for (const utilFunctionsPath of config.utilFunctionsPaths) {
                this.project.addSourceFilesAtPaths(pathToTsGlobPath(utilFunctionsPath))
            }
        }
    }

    public resolveFunction(name: string): FunctionDeclaration | null {
        // We use a secondary cache in case a function is used multiple times.
        let ret: FunctionDeclaration | null = null
        if(this.functionCache.has(name)){
            ret = this.functionCache.get(name) as FunctionDeclaration
        }else {
            for(const sourceFile of this.project.getSourceFiles()){
                // make sure this file is in our configured paths and not just introduced by the ts-config
                if(this.isInFunctionPaths(sourceFile.getFilePath())) {

                    const utilFunction = sourceFile.getFunction(name)
                    if (utilFunction) {
                        ret = utilFunction
                        this.functionCache.set(name, utilFunction)
                        break
                    }

                }
            }
        }
        return ret
    }

    private isInFunctionPaths(pathToCheck: string){
        let ret = false
        if(this.config.utilFunctionsPaths) {

            const absPathToCheck = path.resolve(pathToCheck)

            for (const utilFunctionsPath of this.config.utilFunctionsPaths) {

                let absFuncPath = path.resolve(utilFunctionsPath)
                if(!absFuncPath.endsWith('.ts') && !absFuncPath.endsWith(path.sep)){
                    absFuncPath = absFuncPath + path.sep
                }

                if(absPathToCheck.startsWith(absFuncPath)){
                    ret = true
                    break
                }
            }
        }
        return ret
    }

}
