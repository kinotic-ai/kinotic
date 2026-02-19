/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { IEvent } from './IEventBus.js'

/**
 * Interface for the service context, extendable by users for type-safe context data.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ServiceContext {
  [key: string]: any;
}

/**
 * Interface for interceptors that create or modify the ServiceContext before service method invocation.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export interface ContextInterceptor<T extends ServiceContext> {
  intercept(event: IEvent, context: T): Promise<T> | T;
}
