

import { ContinuumError } from './ContinuumError'

export class AuthorizationError extends ContinuumError {

    constructor(message: string) {
        super(message);
        Object.setPrototypeOf(this, AuthorizationError.prototype);
    }
}
