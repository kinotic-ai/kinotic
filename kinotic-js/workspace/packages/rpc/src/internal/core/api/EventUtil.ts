/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */


import {EventConstants, IEvent} from '@/core/api/IEventBus.js'
import {Event} from '@/core/api/EventBus.js'

/**
 * Utility functions for working with events in the Continuum framework.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export class EventUtil {

    public static createReplyEvent(
        incomingHeaders: Map<string, string>,
        headers?: Map<string, string>,
        body?: Uint8Array
    ): IEvent {
        if (!incomingHeaders) {
            throw new Error("incomingHeaders cannot be null")
        }

        const replyCRI = incomingHeaders.get(EventConstants.REPLY_TO_HEADER)
        if (!replyCRI || replyCRI.trim() === "") {
            throw new Error("No reply-to header found, cannot create outgoing message")
        }

        const newHeaders = new Map<string, string>()
        for (const [key, value] of incomingHeaders) {
            if (key.startsWith("__")) {
                newHeaders.set(key, value)
            }
        }

        if (headers) {
            for (const [key, value] of headers) {
                newHeaders.set(key, value)
            }
        }

        return new Event(replyCRI, newHeaders, body || undefined)
    }
}
