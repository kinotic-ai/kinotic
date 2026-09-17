import type {IEvent} from './event/IEventBus';
import type {Participant} from './security/Participant';

/**
 * The request context a {@link Context} method receives as its final parameter: the calling participant,
 * plus whatever the registered {@link ContextInterceptor} adds. Extend it for type-safe context data.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ServiceContext {
  /**
   * The participant that invoked the service method, as authenticated by the gateway on the calling
   * connection. Absent when the invocation originated inside the platform with no participant bound,
   * such as a scheduled job.
   */
  participant?: Participant;
  [key: string]: any;
}

/**
 * Interface for interceptors that create or modify the ServiceContext before service method invocation.
 * The context handed in already carries the calling participant; the context returned is the one the
 * method receives.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ContextInterceptor<T extends ServiceContext> {
  intercept(event: IEvent, context: T): Promise<T> | T;
}
