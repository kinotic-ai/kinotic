import { describe, it, expect, afterAll } from "vitest"
import { ConnectedInfo, Kinotic, Event, EventConstants, type IEvent } from "../src"
import { TestServiceNoScope } from "./TestServiceNoScope"
import { createConnectOptions, logFailure, validateConnectedInfo } from "./TestHelper"
import { firstValueFrom, Observable } from "rxjs"
import { v4 as uuidv4 } from "uuid"

// Same app-zone setup as Publish.test.ts, under its own application id so the two suites
// never share service registrations on the gateway.
const APP_ID = 'reconnect-app'
const ZONE = `app.kinotic-test.${APP_ID}`

describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe("Publish Durability", () => {
        let replyToId: string

        // The replyToId is server-generated per connection, so it is recaptured on every connect.
        const connect = async (): Promise<void> => {
            const connectedInfo: ConnectedInfo = await logFailure(
                Kinotic.connect(createConnectOptions()),
                "Failed to connect to Kinotic Gateway"
            )
            validateConnectedInfo(connectedInfo)
            replyToId = connectedInfo.replyToId
        }

        const invokeGreet = async (name: string): Promise<any> => {
            const replyTo = `${EventConstants.REPLY_DESTINATION_PREFIX}${replyToId}:${uuidv4()}@continuum.js.EventBus/replyHandler`
            const event = new Event(`srv://${ZONE}~com.example.TestServiceNoScope/greet`, new Map([
                                        [EventConstants.REPLY_TO_HEADER, replyTo],
                                        [EventConstants.CONTENT_TYPE_HEADER, "application/json"],
                                    ]))
            event.setDataString(JSON.stringify([name]))
            const response: Observable<IEvent> = Kinotic.eventBus.observe(replyTo)
            const resultPromise = firstValueFrom(response)
            Kinotic.eventBus.send(event)
            const result = await resultPromise
            if (result.hasHeader(EventConstants.ERROR_HEADER)) {
                throw new Error(result.getHeader(EventConstants.ERROR_HEADER))
            }
            return JSON.parse(result.getDataString())
        }

        afterAll(async () => {
            await expect(Kinotic.disconnect()).resolves.toBeUndefined()
            Kinotic.zonePrefix = null
        })

        it("registers a service instantiated before the first connect", async () => {
            Kinotic.zonePrefix = ZONE
            // Registration queues on the not-yet-connected client and subscribes once
            // the connection comes up.
            new TestServiceNoScope()

            await connect()

            expect(await invokeGreet("Alice")).toBe("Hello, Alice!")
        }, 1000 * 60 * 10)

        it("keeps the service callable across disconnect and reconnect", async () => {
            await Kinotic.disconnect()
            await connect()

            expect(await invokeGreet("Bob")).toBe("Hello, Bob!")
        }, 1000 * 60 * 10)
    })
  })
})
