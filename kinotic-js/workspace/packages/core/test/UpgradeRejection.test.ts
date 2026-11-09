import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { WebSocketServer } from 'ws'
import { describe, expect, it } from 'vitest'
import { EventConstants, type ConnectOptions } from '../src'
import { EventBus } from '../src/api/event/EventBus'
import { ensureNodeWebSocket } from '../src/node'

// a header-auth client dials with the ws WebSocket, which reports the gateway's answer to the upgrade
ensureNodeWebSocket()

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

const REASONS: Record<number, string> = { 401: 'Unauthorized', 503: 'Service Unavailable' }

/**
 * A gateway stand-in on a real port for a header-auth client: the upgrade is answered with the rejection status
 * while one is set and accepted otherwise, onto a STOMP endpoint that answers CONNECT. Every upgrade request is
 * counted, and restart() drops every socket as a redeploy of the server does.
 */
async function headerAuthGateway(): Promise<{
    http: Server,
    options: ConnectOptions,
    upgrades: () => number,
    reject: (status: number | null) => void,
    restart: () => void
}> {
    let rejection: number | null = null
    let upgrades = 0
    const http = createServer((request, response) => response.writeHead(404).end())
    const wss = new WebSocketServer({ noServer: true })
    http.on('upgrade', (request, socket, head) => {
        upgrades++
        if (rejection !== null) {
            socket.write('HTTP/1.1 ' + rejection + ' ' + REASONS[rejection] + '\r\nConnection: close\r\nContent-Length: 0\r\n\r\n')
            socket.destroy()
        } else {
            wss.handleUpgrade(request, socket, head, accepted => wss.emit('connection', accepted, request))
        }
    })
    let replyToCounter = 0
    wss.on('connection', socket => {
        const replyToId = 'reply-' + (++replyToCounter)
        socket.on('message', data => {
            for (const raw of data.toString().split('\0')) {
                const [command, ...headerLines] = raw.replace(/^\n+/, '').split('\n\n')[0]!.split('\n')
                if (command === 'CONNECT' || command === 'STOMP') {
                    const info = JSON.stringify({ replyToId, participant: { id: 'machine-1' } })
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
        credentials: { name: 'machine', resolve: async () => ({ authHeaders: { clientId: 'machine-1', clientSecret: 'secret' } }) }
    }
    return {
        http,
        options,
        upgrades: () => upgrades,
        reject: (status: number | null) => { rejection = status },
        restart: () => {
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

describe('upgrade rejection', () => {

    it('ends the connect on a 401 to the upgrade without another attempt', async () => {
        const gateway = await headerAuthGateway()
        try {
            gateway.reject(401)
            const bus = new EventBus()
            await expect(bus.connect(gateway.options)).rejects.toThrow('Connection rejected with status 401')
            expect(gateway.upgrades()).toBe(1)
            expect(bus.isConnectionActive()).toBe(false)
        } finally {
            closeServer(gateway.http)
        }
    }, 20_000)

    it('retries a 503 to the upgrade within the attempt budget', async () => {
        const gateway = await headerAuthGateway()
        try {
            gateway.reject(503)
            const bus = new EventBus()
            await expect(bus.connect({ ...gateway.options, maxConnectionAttempts: 2 }))
                .rejects.toThrow('Max number of reconnection attempts reached')
            expect(gateway.upgrades()).toBe(2)
        } finally {
            closeServer(gateway.http)
        }
    }, 20_000)

    it('ends an established connection on a 401 to its reconnect', async () => {
        const gateway = await headerAuthGateway()
        try {
            const bus = new EventBus()
            const ends: (Error | null)[] = []
            bus.connectionEnded.subscribe(reason => ends.push(reason))
            await bus.connect(gateway.options)
            expect(bus.isConnected()).toBe(true)

            gateway.reject(401)
            gateway.restart()
            const deadline = Date.now() + 15_000
            while (ends.length < 2 && Date.now() < deadline) {
                await sleep(50)
            }
            expect(ends[0]).toBeNull()
            expect(ends[1]?.message).toBe('Connection rejected with status 401')
            expect(bus.isConnectionActive()).toBe(false)
            expect(gateway.upgrades()).toBe(2)
        } finally {
            closeServer(gateway.http)
        }
    }, 30_000)
})
