import { RpcError } from '@kinotic-ai/core'

// The server's BetaLimitException: a free beta cloud limit, which the portal presents as the plan it is
const BETA_LIMIT_EXCEPTION = 'BetaLimitException'

/**
 * Returns the explanation of the free beta limit an error reached, or null when the error is not a beta
 * limit. Takes the error a service call failed with, or a deployment's `failureMessage`.
 */
export function betaLimitMessage(error: unknown): string | null {
    let ret: string | null = null
    if (error instanceof RpcError) {
        ret = error.exceptionName === BETA_LIMIT_EXCEPTION ? error.message : null
    } else if (typeof error === 'string') {
        // a job run records its failure as the exception's toString(), so the message follows the class name
        const marker = `${BETA_LIMIT_EXCEPTION}: `
        const at = error.indexOf(marker)
        ret = at >= 0 ? error.substring(at + marker.length) : null
    }
    return ret
}
