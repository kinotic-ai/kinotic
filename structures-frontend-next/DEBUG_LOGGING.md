# Debug Logging

This project uses the [debug](https://github.com/debug-js/debug) library for development-time logging.

## Usage

All console logging has been migrated to use the debug library with a `kinotic-ui:` namespace prefix.

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

The following debug namespaces are available:

- `kinotic-ui:login` - Login and OIDC authentication flow
- `kinotic-ui:entity-list` - Entity list operations
- `kinotic-ui:application-list` - Application list operations
- `kinotic-ui:user-state` - User authentication state management
- `kinotic-ui:application-state` - Application state management
- `kinotic-ui:data-insights` - Data insights visualization rendering
- `kinotic-ui:saved-widgets` - Saved widgets loading and management
- `kinotic-ui:saved-widget-item` - Individual widget rendering
- `kinotic-ui:config` - Application configuration loading

### Adding Debug Logging to New Files

1. Import the `createDebug` function:

```typescript
import { createDebug } from '@/util/debug'
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

- **Zero performance impact in production** - Debug logging is compiled out if not enabled
- **Selective logging** - Enable only the modules you care about
- **Color-coded output** - Each namespace gets a different color in the console
- **Timestamp support** - Shows time elapsed between log calls
- **Standard pattern** - Consistent with the continuum-client-js library
