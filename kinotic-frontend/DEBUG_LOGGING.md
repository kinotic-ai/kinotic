# Debug Logging

This project uses the [debug](https://github.com/debug-js/debug) library for development-time logging.

## Usage

Loggers are created with `createDebug(name)` from `@kinotic-ai/frontend-common`, which
prefixes every namespace with `kinotic-ui:`.

### Enable Logging

To enable debug logging in the browser console:

```javascript
// Enable all kinotic-ui logging
localStorage.setItem('debug', 'kinotic-ui:*')

// Enable specific modules
localStorage.setItem('debug', 'kinotic-ui:login,kinotic-ui:entity-list')

// Enable with wildcards
localStorage.setItem('debug', 'kinotic-ui:*,-kinotic-ui:saved-widget-item')
```

Then refresh the page.

### Disable Logging

```javascript
localStorage.removeItem('debug')
```

### Available Namespaces

`packages/common`:

- `kinotic-ui:session-state` — auth/session state management

`apps/portal`:

- `kinotic-ui:continuum-ui` — connection bootstrap
- `kinotic-ui:login` — login and OIDC authentication flow
- `kinotic-ui:members` — organization member management
- `kinotic-ui:application-list`, `kinotic-ui:application-sidebar`, `kinotic-ui:application-state`
- `kinotic-ui:project-list`, `kinotic-ui:new-project-sidebar`
- `kinotic-ui:project-entity-definitions-page`, `kinotic-ui:project-entity-definitions-table`
- `kinotic-ui:entityDefinitions-list`, `kinotic-ui:entity-list`,
  `kinotic-ui:entity-list-entityDefinitions`, `kinotic-ui:entity-list-old`
- `kinotic-ui:crud-table`
- `kinotic-ui:dashboard-view`, `kinotic-ui:dashboard-details`
- `kinotic-ui:data-insights`, `kinotic-ui:saved-widgets`, `kinotic-ui:saved-widget-item`
- `kinotic-ui:graphql-playground`, `kinotic-ui:openapi-playground`

`apps/system`:

- `kinotic-ui:system-login` — system console login

To list the current set: `grep -rn "createDebug(" apps packages --include=*.ts --include=*.vue`.

### Adding Debug Logging to New Files

1. Import the `createDebug` function:

```typescript
import { createDebug } from '@kinotic-ai/frontend-common'
```

2. Create a debug instance with your module name:

```typescript
const debug = createDebug('my-component')
```

3. Use it like console.log:

```typescript
debug('Simple message')
debug('Message with data: %O', someObject)
debug('User %s clicked button', username)
```

### Format Specifiers

The debug library supports printf-style formatting:

- `%O` - Pretty-print objects
- `%o` - Plain object
- `%s` - String
- `%d` - Number
- `%j` - JSON

Example:
```typescript
debug('User %s logged in at %d with profile: %O', user.email, Date.now(), user.profile)
```

## Benefits

- **No output unless enabled** - a disabled namespace is a no-op function, so the arguments
  are still evaluated but nothing is formatted or written
- **Selective logging** - Enable only the modules you care about
- **Color-coded output** - Each namespace gets a different color in the console
- **Timestamp support** - Shows time elapsed between log calls
- **Standard pattern** - Consistent with the `@kinotic-ai/*` client packages
