/**
 * Base error class for all Kinoitc errors
 */
export class KinoticError extends Error {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, KinoticError.prototype);
    }
}
