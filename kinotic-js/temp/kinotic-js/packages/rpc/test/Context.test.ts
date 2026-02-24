/*
 * Copyright 2008-present Mind Ignited and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest"
import { ConnectedInfo, Continuum, Event, EventConstants, IEvent } from "../src"
import { TestServiceWithContext } from "./TestServiceWithContext"
import { createConnectionInfo, logFailure, validateConnectedInfo } from "./TestHelper"
import { firstValueFrom, Observable } from "rxjs"
import { v4 as uuidv4 } from "uuid"

describe("Context Injection", () => {
    let contextService: TestServiceWithContext
    let replyToId: string
    let testInterceptor: { intercept: (event: IEvent, context: any) => Promise<any> }

    beforeAll(async () => {
        const connectionInfo = createConnectionInfo()
        const connectedInfo: ConnectedInfo = await logFailure(
            Continuum.connect(connectionInfo),
            "Failed to connect to Continuum Gateway"
        )
        validateConnectedInfo(connectedInfo)
        replyToId = connectedInfo.replyToId

        // Register service
        contextService = new TestServiceWithContext()

        // Define valid interceptor
        testInterceptor = {
            async intercept(event: IEvent, context: any) {
                const headers = event.headers
                const realm = headers.get("x-realm") || "default"
                return {
                    ...context,
                    realm,
                    apiKey: realm === "tenant1" ? "key1" : "key2"
                }
            }
        }

        // Set up valid interceptor
        Continuum.serviceRegistry.registerContextInterceptor(testInterceptor)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () => {
        await expect(Continuum.disconnect()).resolves.toBeUndefined()
    })

    const createTestEvent = (cri: string, replyTo: string, args?: any[] | null, headers?: Map<string, string>): IEvent => {
        const eventHeaders = headers ? new Map(headers) : new Map()
        eventHeaders.set(EventConstants.REPLY_TO_HEADER, replyTo)
        eventHeaders.set(EventConstants.CONTENT_TYPE_HEADER, "application/json")
        const event = new Event(cri, eventHeaders)
        if (args != null) {
            event.setDataString(JSON.stringify(args))
        }
        return event
    }

    const sendAndReceiveEvent = async (cri: string, args?: any[] | null, headers?: Map<string, string>): Promise<any> => {
        const replyTo = `${EventConstants.SERVICE_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
        const event = createTestEvent(cri, replyTo, args, headers)
        const response: Observable<IEvent> = Continuum.eventBus.observe(replyTo)
        const resultPromise = firstValueFrom(response)
        Continuum.eventBus.send(event)
        const result = await resultPromise
        if (result.hasHeader(EventConstants.ERROR_HEADER)) {
            throw new Error(result.getHeader(EventConstants.ERROR_HEADER))
        }
        return JSON.parse(result.getDataString())
    }

    describe.sequential("Context injection tests", () => {
        it("should inject context with default realm", async () => {
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceWithContext/greetWithContext", ["Alice"])
            expect(result).toBe("Hello, Alice! Realm: default, API Key: key2")
        })

        it("should inject context with tenant1 realm", async () => {
            const headers = new Map([["x-realm", "tenant1"]])
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceWithContext/greetWithContext", ["Bob"], headers)
            expect(result).toBe("Hello, Bob! Realm: tenant1, API Key: key1")
        })

        it("should handle async context injection", async () => {
            const headers = new Map([["x-realm", "tenant2"]])
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceWithContext/fetchDataWithContext", [42], headers)
            expect(result).toEqual({ id: 42, value: "Data for 42", realm: "tenant2", apiKey: "key2" })
        })

        it("should propagate interceptor error", async () => {
            // Set up failing interceptor
            Continuum.serviceRegistry.registerContextInterceptor({
                async intercept(event: IEvent, context: any) {
                    throw new Error("Interceptor failure")
                }
            })
            await expect(sendAndReceiveEvent("srv://com.example.TestServiceWithContext/greetWithContext", ["Charlie"])).rejects.toThrow("Internal server error")

            // Restore valid interceptor
            Continuum.serviceRegistry.registerContextInterceptor(testInterceptor)
        })
    })
})
