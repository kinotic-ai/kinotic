import type { IEvent } from '@/api/event/IEventBus'
import type { Subscription } from 'rxjs'

/**
 * A stream a service is producing for one invocation: the invocation, whose headers address every reply,
 * and the subscription that ends the stream.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 9/10/2026
 */
export interface ActiveStream {
    request: IEvent
    subscription: Subscription
}
