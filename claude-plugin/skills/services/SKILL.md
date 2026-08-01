---
name: services
description: >
  How to write and call Kinotic services: publish TypeScript classes with @Publish,
  version and zone addressing (app.<org>.<app>, app-api, os-api), consuming services
  through hand-written service proxies with invoke and invokeStream, and streaming
  results as RxJS Observables. Use when adding business logic, microservices, APIs,
  service-to-service calls, or real-time streams in a Kinotic application.
---

# Kinotic Services

Every remote call in a Kinotic app — service to service, frontend to backend — goes
over one STOMP-over-WebSocket connection through the RPC gateway, addressed by zone +
qualified name. There is no REST layer for application services.
Docs: <https://kinotic.ai/apps/services/overview>.

## Publishing a service

A plain class becomes remotely callable with `@Publish` — without it, the class is
never registered and proxies cannot reach it:

```typescript
import { Publish, Version } from '@kinotic-ai/core'

@Publish('com.example')
@Version('1.0.0')
export class GreetingService {
    async greet(name: string): Promise<string> {
        return `Hello ${name}`
    }
}
```

`@Publish(namespace?, name?, advertise?)` — `name` defaults to the class name;
`advertise: true` additionally lists the service in the Service Directory (publishing
alone makes it callable but unlisted).

**Zone setup must happen before any `@Publish` class is instantiated.** A microservice
entry point sets the zone prefix from the project config:

```typescript
import { Kinotic } from '@kinotic-ai/core'
import { appZone } from '@kinotic-ai/os-api'
import config from './.config/kinotic.config'

Kinotic.zonePrefix = appZone(config.organizationId, config.applicationId)
// ... instantiate @Publish services, then:
await Kinotic.connect({ host: 'localhost', port: 58503 })
```

This registers services in the application's zone `app.<orgId>.<appId>`. A class-level
`@Zone('billing')` nests a sub-zone (`app.<org>.<app>.billing`); a project-wide default
can be set via the `kinotic.zone` field in `package.json`.

Other service decorators (from `@kinotic-ai/core`):

- `@Version('1.0.0')` — semantic version pinning; callers can address `#<version>`.
- `@Scope` — on a getter/method supplying an instance id, to route calls to one
  specific instance of a service (e.g. per-node or per-device).
- `@Context` — marks a method whose **final** parameter receives the platform-injected
  request context (never sent by the caller).

Details: <https://kinotic.ai/apps/services/publishing-services>.

## Consuming a service — the proxy pattern

Proxies are hand-written: define an interface, delegate each method through
`serviceProxy`:

```typescript
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'

export interface INotificationService {
    sendAlert(message: string): Promise<void>
    watchAlerts(severity: string): Observable<Alert>
}

export class NotificationService implements INotificationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('app.acme-org.orders-app.com.example.NotificationService')
    }

    sendAlert(message: string): Promise<void> {
        return this.serviceProxy.invoke('sendAlert', [message])
    }

    watchAlerts(severity: string): Observable<Alert> {
        return this.serviceProxy.invokeStream('watchAlerts', [severity])
    }
}
```

The address string is load-bearing: `<zone>.<namespace>.<ClassName>`, where the zone is

| Zone | Contains |
|---|---|
| `app.<orgId>.<appId>` | The application's own services |
| `app-api` | Platform data-plane services available to applications |
| `os-api` | Platform management services (organization scope) |

The gateway validates the zone on every send against the authenticated participant — a
wrong zone routes nowhere and can never cross into another application.
Addressing spec: <https://kinotic.ai/platform/reference/cri-format>.

## Streaming

A published method that returns an RxJS `Observable` becomes a streaming endpoint;
consumers call it with `invokeStream(...)` and receive an `Observable`. Values flow
until the consumer unsubscribes or the producer completes. Use for live feeds,
progress, and tailing. Docs: <https://kinotic.ai/apps/services/streaming>.

## Rules of thumb

- One connected `Kinotic` singleton per process; `Kinotic.connect` after plugins and
  zone prefix are set.
- Service classes hold business logic; entities stay dumb data (persistence is the
  entities-and-persistence skill).
- Method arguments and returns must be serializable data; use `Promise` for
  request-response and `Observable` for streams.
