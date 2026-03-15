import {EventConstants, type IEvent} from '@/api/event/IEventBus'
import {EventUtil} from './EventUtil'

/**
 * Return value conversion utilities for service responses.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ReturnValueConverter {
    convert(incomingMetadata: Map<string, string>, returnValue: any): IEvent
}

export class BasicReturnValueConverter implements ReturnValueConverter {
    convert(incomingMetadata: Map<string, string>, returnValue: any): IEvent {
        return EventUtil.createReplyEvent(
            incomingMetadata,
            new Map([[EventConstants.CONTENT_TYPE_HEADER, "application/json"]]),
            new TextEncoder().encode(JSON.stringify(returnValue))
        )
    }
}
