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

## Sign-up analytics

`VITE_GA_MEASUREMENT_ID` turns on Google Analytics for the sign-up funnel only
(`src/composables/useSignUpAnalytics.ts`). Kinotic Cloud's builds set it, in `.env.azure` and
`.env.dev-server`; `.env` does not, so the default build, the `kinotic-ui` image and any self-hosted
deployment load no analytics. The tag loads only on the sign-up pages (`/signup`, `/signup/email`,
`/signup/verify`, `/register`), sends no page views, and reports each page's URL without its query,
which carries the registration token. Leaving a sign-up page for another route loads that route as a
new document, so the tag never runs in the rest of the portal. Besides Google Analytics' automatic
session and engagement events, it records these, each with a `method` of `social` or `email`:

| Event | Recorded when |
|-------|---------------|
| `cloud_signup_started` | a person submits a provider's sign-up button, or the server accepts their email |
| `cloud_signup_completed` | the server creates the organization |

The ID is that of a web data stream for the portal in the Google Analytics property the kinotic.ai
website reports to, so a funnel exploration can follow the website's `cloud_beta_cta_clicked` event to
these. The stream must have **Enhanced measurement** turned off (Admin → Data streams → the stream):
with it on, Google Analytics records the portal's page views from browser history changes even though
the tag sends none. The tag takes storage only as the visitor allowed in the website's cookie banner,
which saves the choice in the `kinotic-consent` cookie on kinotic.ai and its subdomains.
