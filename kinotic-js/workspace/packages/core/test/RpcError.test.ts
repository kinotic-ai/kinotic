import {describe, expect, it} from 'vitest'
import {Event, EventConstants, RpcError} from '../src'

// What a caller can branch on when a service call fails: the exception the server caught, read off the
// error reply the way EventBus reads it, rather than the message alone.
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('RpcError', () => {

        const errorReply = (headers: [string, string][], body?: string): Event => {
            const event = new Event('reply://r:1@kinotic.js.EventBus/replyHandler', new Map(headers))
            if (body !== undefined) {
                event.setDataString(body)
            }
            return event
        }

        it('carries the exception the server describes in a JSON body', () => {
            const error = RpcError.fromEvent(errorReply([
                [EventConstants.ERROR_HEADER, 'Node n2 left the cluster while serving the request to srv://x'],
                [EventConstants.CONTENT_TYPE_HEADER, 'application/json'],
            ], JSON.stringify({
                exceptionName: 'RpcServiceUnavailableException',
                exceptionClass: 'org.kinotic.core.api.exceptions.RpcServiceUnavailableException',
                errorMessage: 'Node n2 left the cluster while serving the request to srv://x',
            })))
            expect(error).toBeInstanceOf(RpcError)
            expect(error).toBeInstanceOf(Error)
            expect(error.exceptionName).toBe('RpcServiceUnavailableException')
            expect(error.exceptionClass).toBe('org.kinotic.core.api.exceptions.RpcServiceUnavailableException')
            expect(error.message).toBe('Node n2 left the cluster while serving the request to srv://x')
        })

        it('falls back to the error header when the reply carries no description', () => {
            const error = RpcError.fromEvent(errorReply([[EventConstants.ERROR_HEADER, 'boom']]))
            expect(error.message).toBe('boom')
            expect(error.exceptionName).toBe('')
        })

        it('falls back to the error header when the body is not the description', () => {
            const error = RpcError.fromEvent(errorReply([
                [EventConstants.ERROR_HEADER, 'boom'],
                [EventConstants.CONTENT_TYPE_HEADER, 'application/json'],
            ], 'not json'))
            expect(error.message).toBe('boom')
            expect(error.exceptionClass).toBe('')
        })
    })
  })
})
