import { KinoticError } from './KinoticError'

export class AuthenticationError extends KinoticError {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, AuthenticationError.prototype);
    }
}
