export class BaseConversionState {

    private readonly _application: string

    /**
     * Create a new instance of the BaseConversionState
     * @param application the application that entities will belong to
     */
    constructor(application: string) {
        this._application = application
    }


    get application(): string {
        return this._application
    }

}
