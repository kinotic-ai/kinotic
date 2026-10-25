# @kinotic-ai/frontend-common

The shared UI of the Kinotic consoles: the PrimeVue theme preset (`KinoticPreset`), `createKinoticApp`
(app creation with the theme, toasts, router, authentication guard and session-loss handling), the
session state, and components such as `FormDrawer`, `SteppingDrawer`, `CrudTable`, `SideBar`,
`PageHeader` and `AuthPageShell`. Use it to build a Vue app that looks and behaves like the Kinotic
consoles.

The package ships ES modules for a bundler such as Vite.

## Installation

```bash
pnpm add @kinotic-ai/frontend-common @kinotic-ai/core vue vue-router primevue @primeuix/themes primeicons
```

### Peer dependencies

Your app provides these, so the package and your app share one copy of each:

| Package | Needed for |
|---|---|
| `vue`, `vue-router` | everything |
| `primevue`, `@primeuix/themes` | the theme and every component |
| `@kinotic-ai/core` | the session state and the connection to the Kinotic server |
| `@kinotic-ai/management-api` | only the `@kinotic-ai/frontend-common/management` entry (optional) |

The components also use [PrimeIcons](https://primevue.org/icons) classes, so import
`primeicons/primeicons.css` once in your app.

## Entries

- `@kinotic-ai/frontend-common`: the theme, `createKinoticApp`, the session state, and the generic components
  and utilities.
- `@kinotic-ai/frontend-common/management`: the components that read Kinotic OS management data (job runs,
  telemetry, workload logs, machines, connected apps, the OAuth consent page). It needs
  `@kinotic-ai/management-api`, with `ManagementApiPlugin` installed on `Kinotic`:

  ```ts
  import { Kinotic } from '@kinotic-ai/core'
  import { ManagementApiPlugin } from '@kinotic-ai/management-api'

  Kinotic.use(ManagementApiPlugin)
  ```

## Styles

Import the package's stylesheet once, before your own:

```ts
import 'primeicons/primeicons.css'
import '@kinotic-ai/frontend-common/style.css'
import './style.css'
```

The components are styled with [Tailwind CSS](https://tailwindcss.com) v4 utilities and the
[`tailwindcss-primeui`](https://github.com/primefaces/tailwindcss-primeui) plugin, which your app's Tailwind
build generates. Tailwind skips `node_modules` when it looks for class names, so point it at the package,
and bind the `dark:` variant to the `.dark` class the theme toggles:

```css
/* src/style.css */
@import "tailwindcss";
@import "tailwindcss-primeui";

@source "../node_modules/@kinotic-ai/frontend-common/dist";

@custom-variant dark (&:where(.dark, .dark *));
```

Tables, ids and code render in `var(--font-mono)`. The Kinotic consoles set it to JetBrains Mono
(`@fontsource-variable/jetbrains-mono`):

```css
@theme {
  --font-mono: 'JetBrains Mono Variable', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
```

## Creating the app

`createKinoticApp` creates the Vue app with the Kinotic theme and returns it unmounted, so you can add
your own plugins before `mount`. Every route needs a user the `sessionState` reports as signed in, unless
its `meta` sets `authenticationRequired: false`; the guard sends a signed-out user to `/login?referer=<path>`.

```ts
import { createKinoticApp, SessionState } from '@kinotic-ai/frontend-common'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LoginPage from './LoginPage.vue'
import HomePage from './HomePage.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        { path: '/login', component: LoginPage, meta: { authenticationRequired: false } },
        { path: '/', component: HomePage },
    ],
})

createKinoticApp({ root: App, router, sessionState: new SessionState() }).mount('#app')
```

`SessionState` connects to the Kinotic server the page was served from. To reach another one, set these
variables in your app's build environment (for example `.env`):

| Variable | Meaning |
|---|---|
| `VITE_KINOTIC_HOST` | the server's host |
| `VITE_KINOTIC_PORT` | the server's port, `58503` when unset |
| `VITE_KINOTIC_USE_SSL` | `true` to connect with TLS; when unset, TLS follows the page's own protocol |

## License

Elastic License 2.0 with the Kinotic Additional Limitations; see [LICENSE](LICENSE).
