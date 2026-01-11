import {PropertyDefinition} from '@mindignited/continuum-idl'
import {FunctionDeclaration} from 'ts-morph'
import {CalculatedPropertyConfiguration, EntityConfiguration, OverrideConfiguration, TransformConfiguration} from '@mindignited/structures-api'
import {UtilFunctionLocator} from '../../UtilFunctionLocator.js'

export class BaseConversionState {

    private readonly utilFunctionLocator: UtilFunctionLocator | null
    private readonly useFullJsonPathForCalculatedProperties: boolean
    private _entityConfiguration: EntityConfiguration | undefined = undefined
    private _exclusionMap: Map<string, void> | null = null
    private readonly _application: string
    private _overridesMap: Map<string, OverrideConfiguration> | null = null
    private _transformsMap: Map<string, TransformConfiguration> | null = null
    private _calculatedPropertiesMap: Map<string, CalculatedPropertyConfiguration[]> | null = null


    /**
     * Create a new instance of the BaseConversionState
     * @param application the application that entities will belong to
     * @param utilFunctionLocator used to locate util functions
     * @param useFullJsonPathForCalculatedProperties
     */
    constructor(application: string,
                utilFunctionLocator: UtilFunctionLocator | null,
                useFullJsonPathForCalculatedProperties = false) {
        this._application = application
        this.utilFunctionLocator = utilFunctionLocator
        this.useFullJsonPathForCalculatedProperties = useFullJsonPathForCalculatedProperties
    }

    get entityConfiguration(): EntityConfiguration | undefined {
        return this._entityConfiguration
    }

    set entityConfiguration(value: EntityConfiguration | undefined) {
        this._entityConfiguration = value
        if (value) {
            if (value.excludeJsonPaths) {
                this._exclusionMap = new Map<string, void>()
                for (const exclusion of value.excludeJsonPaths) {
                    this._exclusionMap.set(exclusion, undefined)
                }
            }
            if (value.overrides) {
                this._overridesMap = new Map<string, OverrideConfiguration>()
                for (const override of value.overrides) {
                    this._overridesMap.set(override.jsonPath, override)
                }
            }
            if (value.transforms) {
                this._transformsMap = new Map<string, TransformConfiguration>()
                for (const transform of value.transforms) {
                    this._transformsMap.set(transform.jsonPath, transform)
                }
            }
            if (value.calculatedProperties) {
                this._calculatedPropertiesMap = new Map<string, CalculatedPropertyConfiguration[]>()
                for (const calculatedProperty of value.calculatedProperties) {
                    let key: string
                    if(this.useFullJsonPathForCalculatedProperties){
                        if(calculatedProperty.entityJsonPath && calculatedProperty.entityJsonPath.length > 0){
                            key = calculatedProperty.entityJsonPath + '.' + calculatedProperty.propertyName
                        }else{
                            key = calculatedProperty.propertyName
                        }
                    }else{
                        key = calculatedProperty.entityJsonPath
                    }

                    if(this._calculatedPropertiesMap.has(key)){
                        this._calculatedPropertiesMap.get(key)?.push(calculatedProperty)
                    }else{
                        this._calculatedPropertiesMap.set(key, [calculatedProperty])
                    }
                }
            }
        } else {
            this._exclusionMap = null
            this._overridesMap = null
            this._transformsMap = null
            this._calculatedPropertiesMap = null
        }
    }

    get application(): string {
        return this._application
    }

    shouldExclude(jsonPath: string): boolean {
        let ret = false
        if (this._exclusionMap) {
            ret = this._exclusionMap.has(jsonPath)
        }
        return ret
    }

    getOverrideType(jsonPath: string): PropertyDefinition | null {
        let ret: PropertyDefinition | null = null
        if (this._overridesMap) {
            const override = this._overridesMap.get(jsonPath)
            if (override) {
                ret = override.propertyDefinition
            }
        }
        return ret
    }

    getTransformFunction(jsonPath: string): FunctionDeclaration | null {
        let ret: FunctionDeclaration | null = null
        if (this._transformsMap) {
            const transform = this._transformsMap.get(jsonPath)
            if (transform) {
                ret = this.getUtilFunctionByName(transform.transformerFunctionName)
                if (!ret) {
                    throw new Error(`No transformer function could be found with name ${transform.transformerFunctionName}`)
                }
            }
        }
        return ret
    }

    getCalculatedProperties(jsonPath: string): CalculatedPropertyConfiguration[] | null {
        let ret: CalculatedPropertyConfiguration[] | null = null
        if (this._calculatedPropertiesMap) {
            ret = this._calculatedPropertiesMap.get(jsonPath) || null
        }
        return ret
    }

    getUtilFunctionByName(name: string): FunctionDeclaration | null {
        if(!this.utilFunctionLocator){
            throw new Error(`Util Function ${name} required, but no UtilFunctionLocator set`)
        }
        return this.utilFunctionLocator.resolveFunction(name)
    }

}
