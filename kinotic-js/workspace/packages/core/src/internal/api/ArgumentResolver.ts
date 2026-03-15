import {EventConstants, type IEvent} from '@/api/event/IEventBus'

/**
 * Argument resolution utilities for service invocation.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ArgumentResolver {
    resolveArguments(event: IEvent): any[]
}

export class JsonArgumentResolver implements ArgumentResolver {
    resolveArguments(event: IEvent): any[] {
        if (this.containsJsonContent(event)) {
            const data = event.getDataString()
            return data ? JSON.parse(data) : []
        }else{
            throw new Error("Currently only JSON content is supported")
        }
    }

    protected containsJsonContent(event: IEvent): boolean {
        const contentType = event.getHeader(EventConstants.CONTENT_TYPE_HEADER)
        return contentType != null && contentType !== "" && contentType === "application/json"
    }
}
