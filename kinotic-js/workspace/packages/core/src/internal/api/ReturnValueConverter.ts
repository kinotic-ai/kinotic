import {EventConstants, type IEvent} from '@/api/event/IEventBus'
import {Util} from './Util'

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
        // A method that returns nothing still owes the caller a body: JSON.stringify(undefined)
        // is undefined, which TextEncoder turns into an empty payload, and the receiver then
        // reads the frame's terminating NUL as the response and fails to parse it. JSON null
        // says the same thing in a form that decodes.
        const json = returnValue === undefined ? 'null' : JSON.stringify(returnValue)
        return Util.createReplyEvent(
            incomingMetadata,
            // A single-value reply is the end of its request, so it carries the completion marker
            // itself, letting any hop holding per-request state release it on this one event.
            new Map([[EventConstants.CONTENT_TYPE_HEADER, "application/json"],
                     [EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_COMPLETE]]),
            new TextEncoder().encode(json)
        )
    }
}
