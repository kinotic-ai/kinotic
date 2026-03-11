

/**
 * Base error class for all Continuum errors
 */
export class ContinuumError extends Error {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, ContinuumError.prototype);
    }
}
