# Environment Variable Setup Guide

`VITE_KINOTIC_HOST`, `VITE_KINOTIC_PORT`, and `VITE_KINOTIC_USE_SSL` point the SPA at
the management server. Both the REST calls (`apiUrl()`) and the STOMP connect (`serverOverrides()`)
read them, in `packages/common/src/util/helpers.ts`. An empty host resolves to the page's own origin.

| Command | Env files | SPA talks to |
|---------|-----------|--------------|
| `pnpm dev` | `.env` | `http://localhost:58503` |
| `pnpm build` | `.env` | `http://localhost:58503` |
| `pnpm build --mode dev-server` | `.env`, `.env.dev-server` | the development server's management API |
| `pnpm build --mode azure` | `.env`, `.env.azure` | the Azure cluster's management API |

Only `VITE_`-prefixed vars reach client code, and the dev server must be restarted to pick
up a change. Precedence is `.env` < `.env.<mode>` < `.env.local`; `.env.local` is gitignored
for machine-specific overrides.
