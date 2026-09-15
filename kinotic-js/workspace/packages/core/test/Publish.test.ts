import { describe, it, expect, beforeAll, afterAll } from "vitest"
import { ConnectedInfo, Kinotic, Event, EventConstants, type IEvent } from "../src"
import { TestServiceNoScope } from "./TestServiceNoScope"
import { TestServiceWithScope } from "./TestServiceWithScope"
import { TestServiceWithScopeOptional } from "./TestServiceWithScopeOptional"
import { createConnectOptions, logFailure, validateConnectedInfo } from "./TestHelper"
import { firstValueFrom, Observable, take, toArray } from "rxjs"
import { v4 as uuidv4 } from "uuid"

// The client hosts these services, so it connects as an organization participant (the app
// runtime's identity) and registers them in an app zone under its org; the org id is
// DEFAULT_AUTH_HEADERS.organizationId.
const APP_ID = 'test-app'
const ZONE = `app.kinotic-test.${APP_ID}`

describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe("Publish Mechanism", () => {
        let noScopeService: TestServiceNoScope
        let scopedService: TestServiceWithScope
        let scopeOptionalService: TestServiceWithScopeOptional
        let replyToId: string

        beforeAll(async () => {
            // Registers this client's services under ZONE; must be set before they are instantiated
            Kinotic.zonePrefix = ZONE
            const connectionInfo = createConnectOptions()
            const connectedInfo: ConnectedInfo = await logFailure(
                Kinotic.connect(connectionInfo),
                "Failed to connect to Kinotic Gateway"
            )
            validateConnectedInfo(connectedInfo)
            replyToId = connectedInfo.replyToId // Capture the replyToId from the server

            // Register services once
            noScopeService = new TestServiceNoScope()
            scopedService = new TestServiceWithScope()
            scopeOptionalService = new TestServiceWithScopeOptional()
        }, 1000 * 60 * 10) // 10 minutes

        afterAll(async () => {
            await expect(Kinotic.disconnect()).resolves.toBeUndefined()
            Kinotic.zonePrefix = null
        })

        // The services register in ZONE, so target their zoned address
        const createTestEvent = (cri: string, replyTo: string, args?: any[] | null): IEvent => {
            const event = new Event(cri.replace('com.example.', `${ZONE}~com.example.`), new Map([
                                                     [EventConstants.REPLY_TO_HEADER, replyTo],
                                                     [EventConstants.CONTENT_TYPE_HEADER, "application/json"],
                                                 ]))
            if (args != null) {
                event.setDataString(JSON.stringify(args))
            }
            return event
        }

        const sendAndReceiveEvent = async (cri: string, args?: any[] | null): Promise<any> => {
            const replyTo = `${EventConstants.REPLY_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
            const event = createTestEvent(cri, replyTo, args)
            const response: Observable<IEvent> = Kinotic.eventBus.observe(replyTo)
            const resultPromise = firstValueFrom(response)
            Kinotic.eventBus.send(event)
            const result = await resultPromise
            if (result.hasHeader(EventConstants.ERROR_HEADER)) {
                throw new Error(result.getHeader(EventConstants.ERROR_HEADER))
            }
            return JSON.parse(result.getDataString())
        }

        // Sends a request and collects every reply for it until the completion control arrives
        const sendAndCollectStream = async (cri: string, args?: any[] | null): Promise<IEvent[]> => {
            const correlationId = uuidv4()
            const replyTo = `${EventConstants.REPLY_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
            const event = createTestEvent(cri, replyTo, args)
            event.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
            const replies: IEvent[] = []
            const done = new Promise<IEvent[]>((resolve, reject) => {
                const subscription = Kinotic.eventBus.observe(replyTo).subscribe({
                    next: reply => {
                        replies.push(reply)
                        if (reply.getHeader(EventConstants.CONTROL_HEADER) === EventConstants.CONTROL_VALUE_COMPLETE
                            || reply.hasHeader(EventConstants.ERROR_HEADER)) {
                            subscription.unsubscribe()
                            resolve(replies)
                        }
                    },
                    error: reject
                })
            })
            Kinotic.eventBus.send(event)
            return done
        }

        describe("Streaming methods", () => {
            it("should stream every value, naming the service on each, then complete", async () => {
                const replies = await sendAndCollectStream("srv://com.example.TestServiceNoScope/countTo", [3])
                const values = replies.slice(0, -1)
                expect(values.map(reply => JSON.parse(reply.getDataString()))).toEqual([1, 2, 3])
                for (const value of values) {
                    expect(value.getHeader(EventConstants.ORIGIN_CRI_HEADER)).toBe(`srv://${ZONE}~com.example.TestServiceNoScope/countTo`)
                    expect(value.hasHeader(EventConstants.CONTROL_HEADER)).toBe(false)
                }
                const completion = replies[replies.length - 1]!
                expect(completion.getHeader(EventConstants.CONTROL_HEADER)).toBe(EventConstants.CONTROL_VALUE_COMPLETE)
                expect(completion.data.isPresent()).toBe(false)
            })

            it("should stop producing once the caller cancels", async () => {
                const correlationId = uuidv4()
                const replyTo = `${EventConstants.REPLY_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
                const event = createTestEvent("srv://com.example.TestServiceNoScope/tick", replyTo, [50])
                event.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
                const replies = Kinotic.eventBus.observe(replyTo)
                const firstTwo = firstValueFrom(replies.pipe(take(2), toArray()))
                Kinotic.eventBus.send(event)
                const received = await firstTwo

                const cancel = new Event(received[0]!.getHeader(EventConstants.ORIGIN_CRI_HEADER)!, new Map([
                    [EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_CANCEL],
                    [EventConstants.CORRELATION_ID_HEADER, correlationId],
                    [EventConstants.REPLY_TO_HEADER, replyTo]
                ]))
                Kinotic.eventBus.send(cancel)

                // once the cancel has landed, a window several periods long stays silent
                let after = 0
                const subscription = replies.subscribe(() => after++)
                await new Promise(resolve => setTimeout(resolve, 200))
                after = 0
                await new Promise(resolve => setTimeout(resolve, 500))
                subscription.unsubscribe()
                expect(after).toBe(0)
            })
        })

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

        describe("ScopeOptional methods", () => {
            it("should answer a ScopeOptional method on the unscoped address", async () => {
                const result = await sendAndReceiveEvent("srv://com.example.TestServiceWithScopeOptional/anyInstanceValue")
                expect(result).toBe("any instance can answer this")
            })

            it("should answer a ScopeOptional method on the scoped address too", async () => {
                const result = await sendAndReceiveEvent("srv://opt-tenant@com.example.TestServiceWithScopeOptional/anyInstanceValue")
                expect(result).toBe("any instance can answer this")
            })

            it("should answer an instance-affine method on the scoped address", async () => {
                const result = await sendAndReceiveEvent("srv://opt-tenant@com.example.TestServiceWithScopeOptional/instanceValue")
                expect(result).toBe("only the named instance can answer this")
            })

            it("should reject an unscoped invocation of an instance-affine method", async () => {
                await expect(sendAndReceiveEvent("srv://com.example.TestServiceWithScopeOptional/instanceValue"))
                    .rejects.toThrow("requires a scoped invocation")
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
  })
})
