import { KinoticError } from './KinoticError.js'

export class AuthorizationError extends KinoticError {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, AuthorizationError.prototype);
    }
}
