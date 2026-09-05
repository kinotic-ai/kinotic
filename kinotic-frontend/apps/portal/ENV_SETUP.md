# Environment Variable Setup Guide

`VITE_KINOTIC_HOST`, `VITE_KINOTIC_PORT`, and `VITE_KINOTIC_USE_SSL` point the SPA at
kinotic-server. Both the REST calls (`apiUrl()`) and the STOMP connect (`serverOverrides()`)
read them, in `packages/common/src/util/helpers.ts`. An empty host resolves same-origin,
which is what the vite dev proxy and the gateway-served production build both need.

| Command | Env files | SPA talks to |
|---------|-----------|--------------|
| `pnpm dev` | `.env` | `http://localhost:58503` |
| `pnpm dev:tunnel` | `.env` + `.env.tunnel` | same origin, through the vite proxy |
| `pnpm build` | `.env` | `http://localhost:58503` |
| `pnpm build:dev` | `.env` | `http://localhost:58503` |
| `pnpm build:kind` | `.env` + `.env.kind` | `https://localhost:58503` |

Only `VITE_`-prefixed vars reach client code, and the dev server must be restarted to pick
up a change. Precedence is `.env` < `.env.<mode>` < `.env.local`; `.env.local` is gitignored
for machine-specific overrides.

## Tunnel mode (ngrok + GitHub/OIDC callbacks)

`pnpm dev:tunnel` serves the SPA and the backend from ONE origin, so external callbacks
reach a local server through a single tunnel. `.env.tunnel` clears `VITE_KINOTIC_HOST`, and
`server.proxy` in `vite.config.ts` forwards `/api`, `/v1` (STOMP WebSocket), `/.well-known`,
and `/mcp` to 58503.

```bash
pnpm dev:tunnel          # vite on :5173, proxying the backend
ngrok http 5173          # one public origin for SPA + API
```

Point the GitHub App callback and webhook URLs at the ngrok origin, then set the server's
`kinotic.domain.appBaseUrl` to it (`KINOTIC_DOMAIN_APPBASEURL=https://<id>.ngrok-free.app`,
or edit `application-development.yml`) — OIDC `redirect_uri`s and email links are built from
it server-side, and `apiBaseUrl` falls back to it.

The tunnel host must be allowed in two places:

- `server.allowedHosts` in `vite.config.ts`, or vite answers `Blocked request`.
- `kinotic.apiGateway.cors.allowedOriginPattern` in `application-development.yml`, or the
  gateway answers `CORS Rejected - Invalid origin`. This applies even though the SPA and API
  share the origin, because the social login forms POST and browsers send `Origin` on a POST
  navigation.
