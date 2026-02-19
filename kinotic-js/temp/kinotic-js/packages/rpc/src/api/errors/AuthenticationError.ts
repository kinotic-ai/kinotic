

import { ContinuumError } from './ContinuumError'

export class AuthenticationError extends ContinuumError {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, AuthenticationError.prototype);
    }
}
