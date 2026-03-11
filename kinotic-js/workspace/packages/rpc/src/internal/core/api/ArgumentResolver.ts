/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */


import {EventConstants, IEvent} from '@/core/api/IEventBus.js'

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
