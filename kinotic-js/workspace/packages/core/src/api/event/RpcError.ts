import {EventConstants, type IEvent} from './IEventBus'

/**
 * The error a service call fails with when the server answered it with an error reply. The reply body
 * describes the exception the server caught, so a caller can branch on which exception it was rather than
 * on a message written for people: a call that failed because the node serving it left the cluster carries
 * `RpcServiceUnavailableException`, one rejected because nothing served its address
 * `RpcMissingServiceException`.
 */
export class RpcError extends Error {

    /**
     * The simple name of the exception the server caught, such as `RpcServiceUnavailableException`.
     */
    public readonly exceptionName: string

    /**
     * The fully qualified class name of the exception the server caught.
     */
    public readonly exceptionClass: string

    constructor(message: string, exceptionName: string, exceptionClass: string) {
        super(message)
        this.name = 'RpcError'
        this.exceptionName = exceptionName
        this.exceptionClass = exceptionClass
    }

    /**
     * Builds the error an error reply carries. The body is the server's description of the exception when
     * it is JSON; otherwise the error header alone names the failure.
     * @param reply the error reply, which carries the error header
     */
    public static fromEvent(reply: IEvent): RpcError {
        const message = reply.getHeader(EventConstants.ERROR_HEADER) ?? ''
        let ret: RpcError
        const detail = describedException(reply)
        if (detail !== undefined) {
            ret = new RpcError(detail.errorMessage ?? message, detail.exceptionName ?? '', detail.exceptionClass ?? '')
        } else {
            ret = new RpcError(message, '', '')
        }
        return ret
    }
}

interface ServiceExceptionWrapper {
    exceptionName?: string
    exceptionClass?: string
    errorMessage?: string
}

// The body is a ServiceExceptionWrapper when the reply is JSON; anything else, or a body that does not
// parse, describes nothing beyond the header
function describedException(reply: IEvent): ServiceExceptionWrapper | undefined {
    let ret: ServiceExceptionWrapper | undefined = undefined
    if (reply.data.isPresent() && reply.getHeader(EventConstants.CONTENT_TYPE_HEADER)?.startsWith('application/json')) {
        try {
            const parsed = JSON.parse(reply.getDataString())
            if (parsed !== null && typeof parsed === 'object') {
                ret = parsed as ServiceExceptionWrapper
            }
        } catch {
            // not a description of the exception, the header is all there is
        }
    }
    return ret
}
