# @kinotic-ai/app-api

Client API for the Kinotic platform's **application plane**: the tenant services addressed in the
`app-api` zone, which an application's own pages call for the user they serve: the user's tenant,
its members, the invitations into it and the grants its users hold on it.

```typescript
import { Kinotic } from '@kinotic-ai/core'
import { AppApiPlugin } from '@kinotic-ai/app-api'

Kinotic.use(AppApiPlugin)
await Kinotic.connect()
const tenant = await Kinotic.tenant.getTenant()
await Kinotic.tenantMembers.inviteMember('colleague@example.com', 'Colleague')
```

The management plane's client API lives in `@kinotic-ai/management-api`.
