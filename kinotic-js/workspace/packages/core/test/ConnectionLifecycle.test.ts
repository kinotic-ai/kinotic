import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { WebSocketServer } from 'ws'
import { describe, expect, it } from 'vitest'
import { firstValueFrom, toArray } from 'rxjs'
import { ConnectedInfo, Event, EventConstants, type IEvent, type ConnectOptions, type IWebSocket } from '../src'
import { EventBus } from '../src/api/event/EventBus'
import { FakeStompServer, type FakeFrame, type FakeSocket } from './FakeStompServer'

const SERVICE = 'srv://test~com.example.Service/echo'

function options(server: FakeStompServer, extra: Partial<ConnectOptions> = {}): ConnectOptions {
    return { server: { host: 'fake', useSSL: false }, webSocketFactory: server.factory as () => IWebSocket, ...extra }
}

// answers every SEND with a single-value reply carrying the request's correlation id
function echo(frame: FakeFrame, socket: FakeSocket): void {
    socket.message(frame.headers[EventConstants.REPLY_TO_HEADER]!, {
        [EventConstants.CORRELATION_ID_HEADER]: frame.headers[EventConstants.CORRELATION_ID_HEADER]!,
        [EventConstants.CONTENT_TYPE_HEADER]: EventConstants.CONTENT_JSON,
        [EventConstants.CONTROL_HEADER]: EventConstants.CONTROL_VALUE_COMPLETE
    }, JSON.stringify('echoed'))
}

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/** An HTTP server that takes every request and never answers it, recording each request's path. */
async function unansweringServer(): Promise<{ http: Server, paths: string[], options: ConnectOptions }> {
    const paths: string[] = []
    const http = createServer(request => paths.push(request.url ?? ''))
    await new Promise<void>(resolve => http.listen(0, '127.0.0.1', resolve))
    const port = (http.address() as AddressInfo).port
    // credentials without headers authenticate with the session cookie, so each attempt checks the session first
    const options: ConnectOptions = {
        server: { host: '127.0.0.1', port, useSSL: false },
        credentials: { name: 'session cookie', resolve: async () => ({}) }
    }
    return { http, paths, options }
}

/**
 * A gateway stand-in on a real port for a cookie-auth client: /api/auth/me answers 204 while signedIn and 401
 * otherwise, and /api/v1 is a STOMP endpoint that answers CONNECT. restart() drops every socket and the session,
 * as a redeploy of the server does.
 */
async function sessionGateway(): Promise<{ http: Server, options: ConnectOptions, signIn: () => void, restart: () => void }> {
    let signedIn = true
    const http = createServer((request, response) => {
        response.writeHead(request.url === '/api/auth/me' && signedIn ? 204 : 401).end()
    })
    const wss = new WebSocketServer({ server: http, path: '/api/v1' })
    let replyToCounter = 0
    wss.on('connection', socket => {
        const replyToId = 'reply-' + (++replyToCounter)
        socket.on('message', data => {
            for (const raw of data.toString().split('\0')) {
                const [command, ...headerLines] = raw.replace(/^\n+/, '').split('\n\n')[0]!.split('\n')
                if (command === 'CONNECT' || command === 'STOMP') {
                    const info = JSON.stringify({ replyToId, participant: { id: 'test' } })
                    socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n' + EventConstants.CONNECTED_INFO_HEADER + ':' + info + '\n\n\0')
                } else if (command === 'DISCONNECT') {
                    const receipt = headerLines.find(line => line.startsWith('receipt:'))
                    if (receipt) {
                        socket.send('RECEIPT\nreceipt-id:' + receipt.substring('receipt:'.length) + '\n\n\0')
                    }
                }
            }
        })
    })
    await new Promise<void>(resolve => http.listen(0, '127.0.0.1', resolve))
    const port = (http.address() as AddressInfo).port
    const options: ConnectOptions = {
        server: { host: '127.0.0.1', port, useSSL: false },
        credentials: { name: 'session cookie', resolve: async () => ({}) }
    }
    return {
        http,
        options,
        signIn: () => { signedIn = true },
        restart: () => {
            signedIn = false
            for (const client of wss.clients) {
                client.terminate()
            }
        }
    }
}

function closeServer(http: Server): void {
    http.closeAllConnections()
    http.close()
}

/** Collects the reason of every connection end that carried one, so an orderly end is not counted. */
function failures(bus: EventBus): Error[] {
    const ret: Error[] = []
    bus.connectionEnded.subscribe(reason => {
        if (reason !== null) {
            ret.push(reason)
        }
    })
    return ret
}

describe('connection lifecycle', () => {

    it('answers a request over a connection and the reply destination names the connection', async () => {
        const server = new FakeStompServer()
        server.onSend = echo
        const bus = new EventBus()
        const connected = await bus.connect(options(server))
        expect(connected.replyToId).toBe('reply-1')

        const reply = await bus.request(new Event(SERVICE))
        expect(JSON.parse(reply.getDataString())).toBe('echoed')
        const send = server.current.received.find(f => f.command === 'SEND')!
        expect(send.headers[EventConstants.REPLY_TO_HEADER]).toMatch(/^reply:\/\/reply-1:[0-9a-f-]{36}@/)
        await bus.disconnect()
    })

    it('fails the calls in flight when the socket drops, reports the end once, and reconnects on a fresh destination', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        let ended = 0
        bus.connectionEnded.subscribe(() => ended++)
        await bus.connect(options(server))
        const firstSocket = server.current

        const pending = bus.request(new Event(SERVICE))
        await sleep(10)
        firstSocket.drop()
        await expect(pending).rejects.toThrow('Connection lost')
        expect(ended).toBe(1)

        // rx-stomp reconnects after its delay plus the client's jitter; the new socket mints a new destination
        server.onSend = echo
        const deadline = Date.now() + 15_000
        while (!bus.isConnected() && Date.now() < deadline) {
            await sleep(50)
        }
        expect(bus.isConnected()).toBe(true)
        expect(server.current).not.toBe(firstSocket)
        await bus.request(new Event(SERVICE))
        const firstReplyTo = firstSocket.received.find(f => f.command === 'SEND')!.headers[EventConstants.REPLY_TO_HEADER]
        const secondReplyTo = server.current.received.find(f => f.command === 'SEND')!.headers[EventConstants.REPLY_TO_HEADER]
        expect(secondReplyTo).not.toBe(firstReplyTo)
        expect(ended).toBe(1)
        await bus.disconnect()
        expect(ended).toBe(2)
    }, 30_000)

    it('reports each connection established, with the connected-info that connection was issued', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        const established: ConnectedInfo[] = []
        bus.connectionEstablished.subscribe(connectedInfo => established.push(connectedInfo))
        await bus.connect(options(server))
        expect(established.map(info => info.replyToId)).toEqual(['reply-1'])

        server.current.drop()
        await sleep(10)
        const deadline = Date.now() + 15_000
        while (!bus.isConnected() && Date.now() < deadline) {
            await sleep(50)
        }
        // the reconnect is a connection of its own, and the server issues it its own reply destination
        expect(established.map(info => info.replyToId)).toEqual(['reply-1', 'reply-2'])
        await bus.disconnect()
    }, 20_000)

    it('rejects a connect still waiting for its socket when disconnect() is called', async () => {
        const server = new FakeStompServer()
        server.silent = true
        const bus = new EventBus()
        const connecting = bus.connect(options(server))
        await sleep(20)
        await bus.disconnect()
        await expect(connecting).rejects.toThrow('Deactivated before the connection was established')
        expect(bus.isConnectionActive()).toBe(false)
    })

    it('connects again while the previous activation is still closing after a failure ended it', async () => {
        const server = new FakeStompServer()
        server.closeDelayMs = 300
        server.onSend = echo
        const bus = new EventBus()
        await bus.connect(options(server))

        // Two ERROR frames before the socket closes: the second lands on the activation the first one ended,
        // so one end is reported, and the reconnect is issued from that notification while the socket closes
        const ends: Error[] = []
        const reconnected = new Promise<void>((resolve, reject) => {
            bus.connectionEnded.subscribe(reason => {
                ends.push(reason as Error)
                bus.connect(options(server)).then(() => resolve(), reject)
            })
        })
        server.current.error('rejected by the gateway')
        server.current.refuse('rejected by the gateway again')
        await reconnected
        await sleep(400)

        expect(ends.length).toBe(1)
        expect(ends[0]!.message).toBe('STOMP connection error')
        expect(bus.isConnected()).toBe(true)
        const reply = await bus.request(new Event(SERVICE))
        expect(JSON.parse(reply.getDataString())).toBe('echoed')
        await bus.disconnect()
    })

    it('closes a socket produced for a connect that was deactivated while the socket was being made', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        const slowFactory = async (): Promise<IWebSocket> => {
            await sleep(200)
            return server.factory()
        }
        const connecting = bus.connect(options(server, { webSocketFactory: slowFactory }))
        await sleep(50)
        await bus.disconnect()
        await expect(connecting).rejects.toThrow('Deactivated before the connection was established')
        await sleep(300)
        expect(server.sockets.length).toBe(1)
        expect(server.sockets[0]!.closedByClient).toBe(true)
    })

    it('rejects the connect with the server defect when CONNECTED carries no connected-info', async () => {
        const server = new FakeStompServer()
        server.connectedInfo = false
        const bus = new EventBus()
        await expect(bus.connect(options(server))).rejects.toThrow('Server did not return proper data for successful login')
        expect(bus.isConnectionActive()).toBe(false)
    })

    it('reports no failure for a socket factory that fails after disconnect() ended its activation', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        const failed = failures(bus)
        const failingFactory = async (): Promise<IWebSocket> => {
            await sleep(100)
            throw new Error('refresh failed')
        }
        const connecting = bus.connect(options(server, { webSocketFactory: failingFactory }))
        await sleep(20)
        await bus.disconnect()
        await expect(connecting).rejects.toThrow('Deactivated before the connection was established')
        await sleep(200)
        expect(failed).toEqual([])
    })

    it('disconnects and connects again after a failure in the attempt itself rejected the connect', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        const failingFactory = async (): Promise<IWebSocket> => {
            throw new Error('refresh failed')
        }
        await expect(bus.connect(options(server, { webSocketFactory: failingFactory }))).rejects.toThrow('WebSocket factory failed')
        await bus.disconnect()
        await bus.connect(options(server))
        expect(bus.isConnected()).toBe(true)
        await bus.disconnect()
    })

    it('settles a disconnect() issued while the session check is still waiting for the server', async () => {
        const server = await unansweringServer()
        try {
            const bus = new EventBus()
            const connecting = bus.connect(server.options)
            await sleep(100)
            expect(server.paths).toEqual(['/api/auth/me'])
            await bus.disconnect()
            await expect(connecting).rejects.toThrow('Deactivated before the connection was established')
        } finally {
            closeServer(server.http)
        }
    })

    it('signs in again after a server restart ended the session and the reconnect found it gone', async () => {
        const gateway = await sessionGateway()
        try {
            const bus = new EventBus()
            const ended = failures(bus)
            await bus.connect(gateway.options)
            expect(bus.isConnected()).toBe(true)

            gateway.restart()
            const deadline = Date.now() + 15_000
            while (ended.length === 0 && Date.now() < deadline) {
                await sleep(50)
            }
            expect(ended.map(e => e.message)).toContain('Authentication required')

            // the console's sign-in: a REST login, then SessionState.login() disconnects and connects again
            gateway.signIn()
            await bus.disconnect()
            await bus.connect(gateway.options)
            expect(bus.isConnected()).toBe(true)
            await bus.disconnect()
        } finally {
            closeServer(gateway.http)
        }
    }, 30_000)

    it('reports the end with its reason when the failure lands with no socket open', async () => {
        const server = new FakeStompServer()
        const bus = new EventBus()
        const ends: (Error | null)[] = []
        bus.connectionEnded.subscribe(reason => ends.push(reason))
        let attempts = 0
        // the reconnect after the drop never gets a socket, so its failure ends the connection from there
        const factory = async (): Promise<IWebSocket> => {
            if (++attempts > 1) {
                throw new Error('refresh failed')
            }
            return server.factory()
        }
        await bus.connect(options(server, { webSocketFactory: factory }))
        server.current.drop()

        const deadline = Date.now() + 15_000
        while (ends.length < 2 && Date.now() < deadline) {
            await sleep(50)
        }
        expect(ends[0]).toBeNull()
        expect(ends[1]?.message).toBe('WebSocket factory failed')
        expect(bus.isConnectionActive()).toBe(false)
    }, 20_000)

    it('reports each attempt that cannot reach the server while connect() keeps trying, and connects once it can', async () => {
        const server = new FakeStompServer()
        server.unreachable = true
        const bus = new EventBus()
        const failed = failures(bus)
        let attemptsFailed = 0
        bus.connectionAttemptFailed.subscribe(() => attemptsFailed++)
        const connecting = bus.connect(options(server))

        const deadline = Date.now() + 5_000
        while (attemptsFailed === 0 && Date.now() < deadline) {
            await sleep(20)
        }
        expect(attemptsFailed).toBe(1)
        expect(bus.isConnectionActive()).toBe(true)

        // rx-stomp retries after its reconnect delay, and that attempt reaches the server
        server.unreachable = false
        const connected = await connecting
        expect(connected.replyToId).toBe('reply-2')
        await bus.disconnect()
        expect(attemptsFailed).toBe(1)
        expect(failed).toEqual([])
    }, 15_000)

    it('closes an attempt the server never answers after the connection timeout, and connects on the retry', async () => {
        const server = new FakeStompServer()
        server.silent = true
        const bus = new EventBus()
        const failed = failures(bus)
        let attemptsFailed = 0
        bus.connectionAttemptFailed.subscribe(() => attemptsFailed++)
        const started = Date.now()
        const connecting = bus.connect(options(server))

        const deadline = Date.now() + 15_000
        while (attemptsFailed === 0 && Date.now() < deadline) {
            await sleep(50)
        }
        expect(attemptsFailed).toBe(1)
        expect(Date.now() - started).toBeGreaterThanOrEqual(10_000)
        expect(server.sockets[0]!.closedByClient).toBe(true)
        expect(bus.isConnectionActive()).toBe(true)

        // rx-stomp retries after its reconnect delay, and the server answers that attempt
        server.silent = false
        const connected = await connecting
        expect(connected.replyToId).toBe('reply-2')
        await bus.disconnect()
        expect(failed).toEqual([])
    }, 30_000)

    it('reports the attempt that exhausts the budget as the end of the connection', async () => {
        const server = new FakeStompServer()
        server.unreachable = true
        const bus = new EventBus()
        const failed = failures(bus)
        let attemptsFailed = 0
        bus.connectionAttemptFailed.subscribe(() => attemptsFailed++)

        await expect(bus.connect(options(server, { maxConnectionAttempts: 1 })))
            .rejects.toThrow('Max number of reconnection attempts reached')
        await sleep(50)
        expect(attemptsFailed).toBe(0)
        expect(failed.map(reason => reason.message)).toEqual(['Max number of reconnection attempts reached'])
    })

    it('reports an attempt the server closes before answering CONNECT, on the last of the budget, as the end of the connection', async () => {
        const server = new FakeStompServer()
        server.silent = true
        const bus = new EventBus()
        const failed = failures(bus)
        let attemptsFailed = 0
        bus.connectionAttemptFailed.subscribe(() => attemptsFailed++)
        const connecting = bus.connect(options(server, { maxConnectionAttempts: 1 }))
        await sleep(50)

        // the socket closes without an error event, so only the close reports the attempt
        server.current.drop()
        await expect(connecting).rejects.toThrow('Max number of reconnection attempts reached')
        expect(attemptsFailed).toBe(0)
        expect(failed.map(reason => reason.message)).toEqual(['Max number of reconnection attempts reached'])
    }, 1_000)

    it('reports no failure when disconnect() closes a socket that was still connecting on the last attempt of the budget', async () => {
        const server = new FakeStompServer()
        server.openDelayMs = 200
        const bus = new EventBus()
        const failed = failures(bus)
        const connecting = bus.connect(options(server, { maxConnectionAttempts: 1 }))
        await sleep(20)
        await bus.disconnect()
        await expect(connecting).rejects.toThrow('Deactivated before the connection was established')
        await sleep(50)
        expect(failed).toEqual([])
        expect(bus.isConnectionActive()).toBe(false)
    })

    it('settles a disconnect() issued right behind a connect() the server never answers', async () => {
        const server = new FakeStompServer()
        server.silent = true
        const bus = new EventBus()
        const connecting = bus.connect(options(server))
        const disconnecting = bus.disconnect()
        await expect(Promise.race([disconnecting, sleep(1000).then(() => { throw new Error('disconnect still pending') })])).resolves.toBeUndefined()
        await expect(connecting).rejects.toThrow('Disconnected before the connection was started')
        expect(bus.isConnectionActive()).toBe(false)
    })

    it('a reconnect attempt of an activation that disconnect() ended never acts on the next activation', async () => {
        const server = new FakeStompServer()
        server.onSend = echo
        const bus = new EventBus()
        const failed = failures(bus)
        let calls = 0
        let reconnectAttemptInFactory: () => void = () => {}
        const attemptReachedFactory = new Promise<void>(resolve => reconnectAttemptInFactory = resolve)
        const factory = async (): Promise<IWebSocket> => {
            const call = ++calls
            // the reconnect attempt of the first activation: disconnect() ends the activation while it waits here
            if (call === 2) {
                reconnectAttemptInFactory()
                await sleep(300)
                throw new Error('refresh failed')
            }
            await sleep(50)
            return server.factory()
        }
        await bus.connect(options(server, { webSocketFactory: factory }))
        server.current.drop()
        await attemptReachedFactory
        await bus.disconnect()
        await bus.connect(options(server, { webSocketFactory: factory }))
        await sleep(300)

        expect(bus.isConnected()).toBe(true)
        expect(failed).toEqual([])
        expect(server.sockets.length).toBe(2)
        const reply = await bus.request(new Event(SERVICE))
        expect(JSON.parse(reply.getDataString())).toBe('echoed')
        await bus.disconnect()
    }, 15_000)

    it('grants a connection the full attempt budget again for its reconnects', async () => {
        const server = new FakeStompServer()
        server.onSend = echo
        const bus = new EventBus()
        const failed = failures(bus)
        await bus.connect(options(server, { maxConnectionAttempts: 1 }))
        server.current.drop()

        const deadline = Date.now() + 15_000
        while (!bus.isConnected() && failed.length === 0 && Date.now() < deadline) {
            await sleep(50)
        }
        expect(failed).toEqual([])
        expect(bus.isConnected()).toBe(true)
        await bus.disconnect()
    }, 20_000)

    it('ends a stream on a bodiless completion without emitting it as a value', async () => {
        const server = new FakeStompServer()
        server.onSend = (frame, socket) => {
            const replyTo = frame.headers[EventConstants.REPLY_TO_HEADER]!
            const correlationId = frame.headers[EventConstants.CORRELATION_ID_HEADER]!
            socket.message(replyTo, { [EventConstants.CORRELATION_ID_HEADER]: correlationId,
                                      [EventConstants.CONTENT_TYPE_HEADER]: EventConstants.CONTENT_JSON }, '1')
            socket.message(replyTo, { [EventConstants.CORRELATION_ID_HEADER]: correlationId,
                                      [EventConstants.CONTROL_HEADER]: EventConstants.CONTROL_VALUE_COMPLETE })
        }
        const bus = new EventBus()
        await bus.connect(options(server))
        const values: IEvent[] = await firstValueFrom(bus.requestStream(new Event(SERVICE), true).pipe(toArray()))
        expect(values.map(v => v.getDataString())).toEqual(['1'])
        await bus.disconnect()
    })

    it('refuses a request while the connection is down instead of caching a stale reply destination', async () => {
        const server = new FakeStompServer()
        server.onSend = echo
        const bus = new EventBus()
        await bus.connect(options(server))
        server.current.drop()
        await sleep(10)
        await expect(bus.request(new Event(SERVICE))).rejects.toThrow('not connected')
        await bus.disconnect()
    })
})
