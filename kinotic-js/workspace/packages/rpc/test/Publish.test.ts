/*
 * Copyright 2008-present Mind Ignited and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest"
import { ConnectedInfo, Continuum, Event, EventConstants, IEvent } from "../src"
import { TestServiceNoScope } from "./TestServiceNoScope"
import { TestServiceWithScope } from "./TestServiceWithScope"
import { createConnectionInfo, logFailure, validateConnectedInfo } from "./TestHelper"
import { firstValueFrom, Observable } from "rxjs"
import { v4 as uuidv4 } from "uuid"

describe("Publish Mechanism", () => {
    let noScopeService: TestServiceNoScope
    let scopedService: TestServiceWithScope
    let replyToId: string

    beforeAll(async () => {
        const connectionInfo = createConnectionInfo()
        const connectedInfo: ConnectedInfo = await logFailure(
            Continuum.connect(connectionInfo),
            "Failed to connect to Continuum Gateway"
        )
        validateConnectedInfo(connectedInfo)
        replyToId = connectedInfo.replyToId // Capture the replyToId from the server

        // Register services once
        noScopeService = new TestServiceNoScope()
        scopedService = new TestServiceWithScope()
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () => {
        await expect(Continuum.disconnect()).resolves.toBeUndefined()
    })

    const createTestEvent = (cri: string, replyTo: string, args?: any[] | null): IEvent => {
        const event = new Event(cri, new Map([
                                                 [EventConstants.REPLY_TO_HEADER, replyTo],
                                                 [EventConstants.CONTENT_TYPE_HEADER, "application/json"],
                                             ]))
        if (args != null) {
            event.setDataString(JSON.stringify(args))
        }
        return event
    }

    const sendAndReceiveEvent = async (cri: string, args?: any[] | null): Promise<any> => {
        const replyTo = `${EventConstants.SERVICE_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
        const event = createTestEvent(cri, replyTo, args)
        const response: Observable<IEvent> = Continuum.eventBus.observe(replyTo)
        const resultPromise = firstValueFrom(response)
        Continuum.eventBus.send(event)
        const result = await resultPromise
        if (result.hasHeader(EventConstants.ERROR_HEADER)) {
            throw new Error(result.getHeader(EventConstants.ERROR_HEADER))
        }
        return JSON.parse(result.getDataString())
    }

    describe("Non-async methods without scope", () => {
        it("should invoke greet synchronously", async () => {
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/greet", ["Alice"])
            expect(result).toBe("Hello, Alice!")
        })

        it("should invoke combine with multiple args synchronously", async () => {
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/combine", ["test", 42])
            expect(result).toBe("test - 42")
        })
    })

    describe("Async methods without scope", () => {
        it("should invoke fetchData asynchronously", async () => {
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/fetchData", [42])
            expect(result).toEqual({ id: 42, value: "Data for 42" })
        })

        it("should invoke multiArgs with multiple args asynchronously", async () => {
            const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/multiArgs", [1, "two", true])
            expect(result).toEqual({ x: 1, y: "two", z: true })
        })
    })

    describe("Non-async methods with scope", () => {
        it("should invoke greet with scope synchronously", async () => {
            const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/greet", ["Bob"])
            expect(result).toBe("Hello, Bob from tenant!")
        })

        it("should invoke combine with scope synchronously", async () => {
            const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/combine", ["test", 42])
            expect(result).toBe("test - 42 in tenant")
        })
    })

    describe("Async methods with scope", () => {
        it("should invoke fetchData with scope asynchronously", async () => {
            const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/fetchData", [99])
            expect(result).toEqual({ id: 99, value: "Data for 99", scope: "tenant" })
        })

        it("should invoke multiArgs with scope asynchronously", async () => {
            const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/multiArgs", [1, "two", true])
            expect(result).toEqual({ x: 1, y: "two", z: true, scope: "tenant" })
        })
    })

    describe("Method parsing and conflict avoidance", () => {
        it("should distinguish between methods with different names (no scope)", async () => {
            const greetResult = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/greet", ["Charlie"])
            const combineResult = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/combine", ["test", 123])
            expect(greetResult).toBe("Hello, Charlie!")
            expect(combineResult).toBe("test - 123")
        })

        it("should distinguish between methods with different names (with scope)", async () => {
            const greetResult = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/greet", ["Charlie"])
            const combineResult = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/combine", ["test", 123])
            expect(greetResult).toBe("Hello, Charlie from tenant!")
            expect(combineResult).toBe("test - 123 in tenant")
        })

        it("should handle scoped vs unscoped services independently", async () => {
            const noScopeResult = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/greet", ["Dave"])
            const scopeResult = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/greet", ["Eve"])
            expect(noScopeResult).toBe("Hello, Dave!")
            expect(scopeResult).toBe("Hello, Eve from tenant!")
        })
    })

    describe("Error propagation", () => {
        describe("Non-async errors without scope", () => {
            it("should propagate synchronous error", async () => {
                await expect(sendAndReceiveEvent("srv://com.example.TestServiceNoScope/failSync")).rejects.toThrow("Sync failure")
            })
        })

        describe("Async errors without scope", () => {
            it("should propagate asynchronous error", async () => {
                await expect(sendAndReceiveEvent("srv://com.example.TestServiceNoScope/failAsync")).rejects.toThrow("Async failure")
            })
        })

        describe("Non-async errors with scope", () => {
            it("should propagate synchronous error with scope", async () => {
                await expect(sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/failSync")).rejects.toThrow("Scoped sync failure")
            })
        })

        describe("Async errors with scope", () => {
            it("should propagate asynchronous error with scope", async () => {
                await expect(sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/failAsync")).rejects.toThrow("Scoped async failure")
            })
        })

        describe("Argument count mismatch", () => {
            it("should fail when too many arguments are provided to greet", async () => {
                await expect(sendAndReceiveEvent("srv://com.example.TestServiceNoScope/greet", ["Alice", "Extra"])).rejects.toThrow(
                    "Argument count mismatch for method greet: expected 1, got 2"
                )
            })

            it("should fail when too few arguments are provided to combine", async () => {
                await expect(sendAndReceiveEvent("srv://com.example.TestServiceNoScope/combine", ["test"])).rejects.toThrow(
                    "Argument count mismatch for method combine: expected 2, got 1"
                )
            })
        })

        describe("Complex object handling", () => {
            it("should process complex object without scope", async () => {
                const complexObj = { name: "Alice", age: 30, details: { active: true, score: 85 } }
                const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/processComplexObject", [complexObj])
                expect(result).toBe("Alice is 30 years old, active: true, score: 85")
            })

            it("should process complex object with scope", async () => {
                const complexObj = { name: "Bob", age: 25, details: { active: false, score: 90 } }
                const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/processComplexObject", [complexObj])
                expect(result).toBe("Bob is 25 years old, active: false, score: 90 in tenant")
            })

            it("should process list of complex objects without scope", async () => {
                const list = [
                    { id: 1, tags: ["a", "b"] },
                    { id: 2, tags: ["c"] }
                ]
                const result = await sendAndReceiveEvent("srv://com.example.TestServiceNoScope/processListOfComplexObjects", [list])
                expect(result).toBe(6) // 1 + 2 (ids) + 2 + 1 (tag counts) = 6
            })

            it("should process list of complex objects with scope", async () => {
                const list = [
                    { id: 1, tags: ["a", "b"] },
                    { id: 2, tags: ["c"] }
                ]
                const result = await sendAndReceiveEvent("srv://tenant@com.example.TestServiceWithScope/processListOfComplexObjects", [list])
                expect(result).toBe(12) // 1 + 2 (ids) + 2 + 1 (tag counts) + 6 (tenant.length) = 12
            })
        })
    })
})
