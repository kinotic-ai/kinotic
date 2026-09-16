# Publishing Services

> How to publish TypeScript services in Kinotic using decorators.

Use the `@Publish` decorator to expose any TypeScript class as a remote service. The namespace you pass to `@Publish` combined with the class name forms the service identifier that callers use to reach it.

## Basic Example

```typescript
import { Publish, Version } from '@kinotic-ai/core'

@Version('1.0.0')
@Publish('com.example')
class GreetingService {
    hello(name: string): string {
        return `Hello, ${name}!`
    }

    add(a: number, b: number): number {
        return a + b
    }
}
```

This registers the service as `com.example.GreetingService` at version `1.0.0`, in the zone the client is configured for (see [Zones](#zones) below — an application's services register under its `app.<org>.<app>` zone). Every public method on the class becomes a remotely callable operation.

## Zones

Every service address lives in a zone — the isolation boundary the gateway validates on each call (see [CRI Format](/platform/reference/cri-format)). An application's services always register inside the application's own zone: the client's `zonePrefix` is set from static project configuration before services are instantiated, and declared zones are appended to it, so a service can never register outside its application.

```typescript
import { Kinotic } from '@kinotic-ai/core'
import { appZone } from '@kinotic-ai/management-api'
import config from './.config/kinotic.config'

// In your application's entry point, before any @Publish class is instantiated
Kinotic.zonePrefix = appZone(config.organizationId, config.applicationId)
```

`@Publish` classes may be instantiated before or after `Kinotic.connect()`: a
registration made before the connection is up queues and subscribes once it is, and
every registration re-subscribes automatically on reconnect — including across an
explicit `disconnect()` / `connect()` cycle.

```typescript
@Publish()                       // → srv://app.acme-org.orders-app~OrderService
class OrderService { ... }

@Zone('billing')                 // → srv://app.acme-org.orders-app.billing~InvoiceService
@Publish()
class InvoiceService { ... }
```

A default zone for every service in the project can also be declared in `package.json`; a class-level `@Zone` overrides it:

```json
{ "kinotic": { "zone": "billing" } }
```

```typescript
import pkg from './package.json' with { type: 'json' }
Kinotic.defaultZone = pkg.kinotic?.zone ?? null
```

The gateway rejects any send or subscribe outside the zones the authenticated participant may address, so a wrong or missing zone routes nowhere — it can never reach another application.

## Registering on a specific client

`@Publish` registers every instance with the global `Kinotic` client. A service hosted on a client you created yourself — a second `KinoticSingleton`, connected under its own credentials or to another server — is registered through that client's `serviceRegistry` with a `ServiceIdentifier` naming the namespace, the service name, and the full zone:

```typescript
import { KinoticSingleton, ServiceIdentifier } from '@kinotic-ai/core'

const host = new KinoticSingleton()
await host.connect({ server: { host: 'localhost', port: 58503 }, credentials })

const identifier = new ServiceIdentifier('com.example', 'GreetingService', 'app.acme-org.orders-app')
host.serviceRegistry.register(identifier, new GreetingService())   // → srv://app.acme-org.orders-app~com.example.GreetingService

// later, when the service should no longer be reachable
host.serviceRegistry.unRegister(identifier)
```

The zone is the complete zone the service lives in: `zonePrefix` and `defaultZone` apply to `@Publish` classes only. Registering the same identifier twice on one client is a no-op, and `unRegister` ends any stream the service is still producing with an error to its caller.

## Decorators

### @Publish(namespace?, name?, advertise?)

Marks a class for publication. The full service identifier becomes `namespace.ClassName`; both parts are optional (the name defaults to the class name). Passing `advertise` as `true` additionally advertises the service in the platform service directory, so it appears in directory listings for browsing and invocation — publishing alone makes a service callable over RPC without listing it.

### @Zone(zone)

Declares the zone the service is addressable in, relative to the client's `zonePrefix`. The zone is one or more dot-separated labels of lowercase letters, digits, and interior dashes.

### @Version(version)

Sets the semantic version for the service in `X.Y.Z` format. Callers can pin to a specific version when resolving the service.

### @Scope

A getter or method decorator that marks the member providing the scope identifier. Scope targets requests at one specific instance of a service — for example the copy running on a particular node or device.

```typescript
import { Publish, Scope } from '@kinotic-ai/core'

@Publish('com.example')
class DeviceService {
    deviceId: string

    @Scope
    get scope(): string {
        return this.deviceId
    }

    constructor(deviceId: string) {
        this.deviceId = deviceId
    }

    getStatus(): string {
        return `Status for device ${this.deviceId}`
    }
}
```

When a caller targets a specific scope (e.g., `device-42`), the platform routes the request to the instance whose `deviceId` matches.

### @Context

A method decorator that marks a method as receiving the request context. The context carries information about the caller, such as the authenticated participant.

**The context parameter must be the method's final parameter.** Callers never pass it — the platform appends it after the caller-supplied arguments.

```typescript
import { Publish, Context } from '@kinotic-ai/core'

@Publish('com.example')
class AuditService {
    @Context
    logAction(action: string, ctx: any): void {
        console.log(`${ctx.participant.id} performed ${action}`)
    }
}
```

The context parameter is invisible to callers. They do not pass it as an argument; the platform injects it automatically.
