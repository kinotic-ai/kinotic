import { KinoticError } from './KinoticError'

export class AuthorizationError extends KinoticError {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, AuthorizationError.prototype);
    }
}
