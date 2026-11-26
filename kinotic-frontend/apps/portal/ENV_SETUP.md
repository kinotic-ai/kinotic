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

## Analytics

`VITE_GA_MEASUREMENT_ID` and `VITE_CLARITY_PROJECT_ID` turn on Google Analytics and Microsoft Clarity
(`src/util/analytics.ts`). Only Kinotic Cloud's builds set them, in `.env.azure` and `.env.dev-server`,
to the property and project the kinotic.ai website reports to; `.env` sets neither, so the default
build, the `kinotic-ui` image and any self-hosted deployment load no analytics. Both providers start
cookieless and take storage only as the visitor allowed in the website's cookie banner, which it saves
in the `kinotic-consent` cookie on kinotic.ai and its subdomains.

The portal records the sign-up funnel that the website's `cloud_beta_cta_clicked` event begins, each
with a `method` of `social` or `email` (a Clarity session tag `signup_method`):

| Event | Recorded when |
|-------|---------------|
| `cloud_signup_started` | a person submits a provider's sign-up button, or the server accepts their email |
| `cloud_signup_completed` | the server creates the organization |
