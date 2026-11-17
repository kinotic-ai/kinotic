import {EventConstants, type IEvent} from '@/api/event/IEventBus'

/**
 * Argument resolution utilities for service invocation.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ArgumentResolver {
    /**
     * Resolves the arguments an invocation carries, in the order the function takes them.
     * @param event the invocation
     * @param parameterNames the names of the function's parameters in order, as the service's definition
     *        declares them, or null when no definition is declared for the service
     */
    resolveArguments(event: IEvent, parameterNames: string[] | null): any[]
}

export class JsonArgumentResolver implements ArgumentResolver {
    resolveArguments(event: IEvent, parameterNames: string[] | null): any[] {
        let ret: any[]
        const contentType = event.getHeader(EventConstants.CONTENT_TYPE_HEADER)
        const data = event.getDataString()
        if (contentType === EventConstants.CONTENT_JSON) {
            ret = data ? JSON.parse(data) : []
        } else if (contentType === EventConstants.CONTENT_NAMED_JSON) {
            if (!parameterNames) {
                throw new Error('Arguments by name need the service\'s definition, which the project declares by importing its ServiceDefinitions module')
            }
            const body = data ? JSON.parse(data) : {}
            if (body === null || typeof body !== 'object' || Array.isArray(body)) {
                throw new Error(`A ${EventConstants.CONTENT_NAMED_JSON} body must be a JSON object`)
            }
            for (const name of Object.keys(body)) {
                if (!parameterNames.includes(name)) {
                    throw new Error(`Received argument '${name}' which matches no parameter`)
                }
            }
            ret = parameterNames.map(name => body[name] ?? null)
        } else {
            throw new Error("Currently only JSON content is supported")
        }
        return ret
    }
}
