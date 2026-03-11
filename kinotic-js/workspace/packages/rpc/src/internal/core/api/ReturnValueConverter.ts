/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import {EventConstants, IEvent} from '@/core/api/IEventBus.js'
import {EventUtil} from '@/internal/core/api/EventUtil.js'

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
