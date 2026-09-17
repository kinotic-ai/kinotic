/**
 * Thrown when the calling participant is not permitted to do what it asked. A service that throws it
 * fails the invocation with an error reply named {@code AuthorizationException}, the name a Java
 * service's refusal carries, so a caller reads both the same way.
 */
export class AuthorizationException extends Error {
    constructor(message: string = 'Access denied') {
        super(message)
        this.name = 'AuthorizationException'
    }
}
