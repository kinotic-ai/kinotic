import {IConversionContext} from './IConversionContext.js'
import {IConverterStrategy} from './IConverterStrategy.js'
import {Logger} from '../Logger.js'
import {ITypeConverter} from './ITypeConverter.js'

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
export class DefaultConversionContext<T, R, S> implements IConversionContext<T, R, S> {

    private readonly strategy: IConverterStrategy<T, R, S>
    private readonly conversionDepthStack: Array<T> = []
    private readonly errorStack: Array<T> = []
    private readonly _state: S
    private readonly logger: Logger
    private _actualJsonPath: string = ''
    private _actualPropertyStack: string[] = []
    public propertyStack: string[] = []
    public currentJsonPath: string = ''

    constructor(strategy: IConverterStrategy<T, R, S>) {
        this.strategy = strategy
        let state = strategy.initialState()
        if(state instanceof Function){
            this._state = state()
        }else{
            this._state = state
        }
        let logger = strategy.logger()
        if(logger instanceof Function){
            this.logger = logger()
        }else{
            this.logger = logger
        }
    }

    public beginProcessingProperty(name: string): void {
        this.currentJsonPath = (this.propertyStack.length > 0  ? this.propertyStack[this.propertyStack.length - 1] + '.' : '') + name
        this.propertyStack.push(this.currentJsonPath)

        this._actualJsonPath = (this._actualPropertyStack.length > 0  ? this._actualPropertyStack[this._actualPropertyStack.length - 1] + '.' : '') + name
        this._actualPropertyStack.push(this._actualJsonPath)
    }

    public endProcessingProperty(): void {
        this.propertyStack.pop()
        this.currentJsonPath = this.propertyStack.length > 0 ? this.propertyStack[this.propertyStack.length - 1] : ''

        this._actualPropertyStack.pop()
        this._actualJsonPath = this._actualPropertyStack.length > 0 ? this._actualPropertyStack[this._actualPropertyStack.length - 1] : ''
    }

    public get actualJsonPath(): string {
        return this._actualJsonPath
    }

    public get actualPropertyStack(): string[] {
        return this._actualPropertyStack
    }

    public convert(value: T): R {
        try {

            this.conversionDepthStack.unshift(value)

            let converter = this.selectConverter(value)
            if (converter != null) {

                return converter.convert(value, this)

            }else{
                // this causes the stack to unwind, so this is intentional
                // noinspection ExceptionCaughtLocallyJS
                throw new Error('No ITypeConverter can be found for ' + this.strategy.valueToString(value) + '\nWhen using strategy ' + this.strategy.constructor.name)
            }
        } catch (e: any) {
            this.logException(e)
            throw e
        } finally {
            this.conversionDepthStack.shift()
        }
    }

    public state(): S {
        return this._state
    }

    private selectConverter(value: T): ITypeConverter<T, R, S> | null{
        let ret: ITypeConverter<T, R, S> | null = null
        for (let converter of this.strategy.typeConverters()) {
            if(converter.supports(value, this.state())){
                ret = converter
                break
            }
        }
        return ret
    }

    /**
     * Log an exception when appropriate dealing with only logging once even when recursion has occurred
     * @param e to log
     */
    private logException(e: Error) {
        // This indicates this is the first time logException has been called for this context.
        // This would occur at the furthest call depth so at this point the conversionDepthStack has the complete stack
        if(this.errorStack.length === 0){
            // We loop vs add all to keep stack intact
            for(let value of this.conversionDepthStack){
                this.errorStack.unshift(value)
            }
        }
        if(this.conversionDepthStack.length === 1) { // we are at the top of the stack during recursion
            let sb: string = `\nError occurred during conversion.\nException: ${e.message}\nJsonPath: ${this.currentJsonPath}\nConversion Stack:\n`
            let objectCount = 1
            for (let value of this.errorStack) {
                const spacer = '  '.repeat(objectCount)
                sb += spacer
                sb += '- '
                sb += this.strategy.valueToString(value)
                          .replaceAll('\n', '\n  ' + spacer)
                sb += '\n'
                objectCount++
            }
            this.logger.log(sb)
            this.errorStack.length = 0 // we have printed reset
        }
    }

}
