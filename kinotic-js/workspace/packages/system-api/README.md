# @kinotic-ai/system-api

Client API for the Kinotic platform's **system plane**: the node and workload orchestration
services addressed in the `system` zone. Consumed by platform machines (the vm-manager) and
system tooling — never by customer applications.

```typescript
import { Kinotic } from '@kinotic-ai/core'
import { SystemApiPlugin } from '@kinotic-ai/system-api'

Kinotic.use(SystemApiPlugin)
await Kinotic.connect()
await Kinotic.workloadOrchestration.deployWorkload(workload)
```

The management plane's client API lives in `@kinotic-ai/management-api`.
