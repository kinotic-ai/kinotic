We serve every published UI at `<org>-<app>-<ui>.apps.kinotic.ai` through one Front Door
wildcard domain, and the API at `api.kinotic.ai` on the cluster's load balancer. I want a
customer to be able to bring their own domain: `ui.mydomain.com` for a published UI and
`api.mydomain.com` for the API, so the session cookie stays same-site. A customer domain may
take up to half an hour to come live; that is fine. The platform hostname must keep serving
throughout, and nothing about a customer domain may slow down publishing to the platform
hostname, which is an upload and nothing else.

What I already know, so you don't re-derive it:

- Front Door is the UI CDN only. Its WebSocket limits (3,000 concurrent connections per
  profile, 4-hour maximum, 5-minute idle) rule it out for the STOMP gateway, so
  `api.mydomain.com` never goes through Front Door.
- On the UI side a customer domain is a Front Door custom domain of its own, the non-wildcard
  case: a managed certificate works, the customer proves ownership with the TXT record Front
  Door issues plus a CNAME to the endpoint. The wildcard rewrite keys storage by the platform
  hostname (`/sites/{hostname}/…`), so one rule per customer domain maps
  `HostName equals ui.mydomain.com` onto the site's directory. Both are Front Door writes,
  15 to 30 minutes to propagate. Front Door caps custom domains per profile in the hundreds;
  the platform hostnames under the wildcard don't count, but a second profile is needed
  eventually.
- On the API side, TLS for a hostname we don't own. Preferred: Caddy 2 in front of the
  gateway with on-demand TLS, its `ask` endpoint hitting the gateway, which answers whether
  the hostname is a registered, validated domain of an application; certificates issued at
  the first handshake, stored in shared storage when Caddy has replicas. Alternative: Vert.x
  serving certificates by SNI with `HttpServer.updateSSLOptions`, which means owning the
  ACME client, challenges, storage and renewal ourselves.
- The cookie needs nothing: `__Host-kinotic-session` carries no `Domain`, so it belongs to
  `api.mydomain.com`, and `SameSite=Lax` sends it from `ui.mydomain.com` because both share
  the site `mydomain.com`. `SameSite=None` stays a developer-profile setting only.
- What the platform has to learn: a record on the application of its domains and their
  validation state (the Front Door validation token for the UI domain, and how the API
  domain is validated); a CORS check that consults that record instead of the static
  `allowedOriginPattern`; OIDC and social-login redirect URIs built from the request's host
  when it arrived on a customer domain (`AuthEndpointSupport`, which today builds them from
  `appBaseUrl` / `apiBaseUrl`); and a console flow where the customer adds the domain, sees
  the records to create, and watches validation.

Design the domain record, the validation flow for both hostnames, the Caddy `ask` contract,
the CORS and redirect-URI changes, and the console pages, as a series of phases of about ten
files each, with a review after each phase. Start with the UI domain; the API domain and
Caddy come after, since a customer UI on its own domain talking to `api.kinotic.ai` works
already with `SameSite=None` off the table only once the API domain exists.
