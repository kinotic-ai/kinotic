import { describe, it, expect } from "vitest"
import { EventConstants } from "../src"
import { BasicReturnValueConverter } from "../src/internal/api/ReturnValueConverter"

// What goes on the wire for a return value, read back off the event rather than asserted
// against the converter's internals. A body that cannot be parsed is the failure this guards:
// the receiver reads the frame's terminating NUL as the response and rejects the whole reply.
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('BasicReturnValueConverter', () => {

        const converter = new BasicReturnValueConverter()
        const incoming = new Map<string, string>([
            [EventConstants.REPLY_TO_HEADER, 'reply:test'],
            [EventConstants.CORRELATION_ID_HEADER, 'c1'],
        ])

        const bodyOf = (returnValue: any): string =>
            converter.convert(incoming, returnValue).getDataString()

        it('sends JSON null for a method that returns nothing', () => {
            expect(bodyOf(undefined)).toBe('null')
        })

        it('sends JSON null for an explicit null, the same value the caller sees', () => {
            expect(bodyOf(null)).toBe('null')
        })

        it('sends every other value as its JSON', () => {
            expect(bodyOf({ id: 'wl-1', running: true })).toBe('{"id":"wl-1","running":true}')
            expect(bodyOf(['a', 'b'])).toBe('["a","b"]')
            expect(bodyOf(7)).toBe('7')
            expect(bodyOf('done')).toBe('"done"')
            expect(bodyOf(false)).toBe('false')
        })

        it('always writes a body that parses', () => {
            for (const value of [undefined, null, 0, '', false, [], {}]) {
                expect(() => JSON.parse(bodyOf(value))).not.toThrow()
            }
        })

        it('declares the body it wrote as JSON', () => {
            const event = converter.convert(incoming, undefined)
            expect(event.getHeader(EventConstants.CONTENT_TYPE_HEADER)).toBe('application/json')
        })
    })
  })
})
