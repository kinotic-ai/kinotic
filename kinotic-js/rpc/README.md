![CI](https://github.com/MindIgnited/continuum-client-js/actions/workflows/ci.yml/badge.svg?branch=main)

# Continuum Client

The Continuum client JavaScript/TypeScript library for connecting to Continuum servers via STOMP over WebSocket.

For full documentation, see the [Continuum Framework Guide](https://mindignited.github.io/continuum-framework/website/guide/clients.html#typescript-javascript-client).

## Installation

```bash
npm install @mindignited/continuum-client
# or
pnpm add @mindignited/continuum-client
```

## Quick Start

```typescript
import { Continuum, ConnectionInfo } from '@mindignited/continuum-client'

const connectionInfo: ConnectionInfo = {
  host: 'localhost',
  port: 58503,
  useSSL: false,
  connectHeaders: {
    login: 'your-participant-id'
  }
}

const connectedInfo = await Continuum.connect(connectionInfo)
console.log('Connected:', connectedInfo)
```

## Node.js Usage

When using this library in Node.js (CLI tools, scripts, tests), you must provide a WebSocket polyfill since Node.js doesn't have a native `WebSocket` global:

```typescript
import { WebSocket } from 'ws'
Object.assign(global, { WebSocket })

// Now import and use Continuum
import { Continuum } from '@mindignited/continuum-client'
```

> **Important:** The WebSocket polyfill must be applied *before* importing or using any Continuum functions.

## Debugging

Enable STOMP protocol debugging to diagnose connection issues:

```bash
DEBUG=continuum:stomp node your-script.js
# or
DEBUG=continuum:stomp your-cli-command
```

This outputs detailed STOMP protocol messages:
```
continuum:stomp Opening Web Socket... +0ms
continuum:stomp >>> CONNECT
continuum:stomp <<< CONNECTED
continuum:stomp >>> SUBSCRIBE
```

## SSL/TLS with Self-Signed Certificates

When connecting to servers with self-signed certificates (common in local development), Node.js will reject the connection by default. You'll see symptoms like:

```
continuum:stomp Opening Web Socket... +0ms
continuum:stomp Connection closed to wss://your-host/v1 +5s
continuum:stomp STOMP: scheduling reconnection in 2000ms +1ms
```

### Solutions

**Option 1: Trust the CA certificate (Recommended)**

```bash
export NODE_EXTRA_CA_CERTS=/path/to/ca-certificate.pem
node your-script.js
```

**Option 2: Disable certificate verification (Development only!)**

```bash
NODE_TLS_REJECT_UNAUTHORIZED=0 node your-script.js
```

> ⚠️ **Warning:** Never disable certificate verification in production. This makes your connection vulnerable to man-in-the-middle attacks.


## Local Development

```bash
# Install dependencies
pnpm install

# Run tests
pnpm test

# Run tests with coverage
pnpm coverage

# Interactive test UI
pnpm ui-test
```

## Build and Publish

```bash
pnpm build
export NPM_TOKEN=your_access_token
pnpm publish
```
