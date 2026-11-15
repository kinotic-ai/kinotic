# sites-worker

The Cloudflare Worker that serves every organization's published sites, and the platform's own UIs
from the same account. It is bound to the site hosts of the sites domain, `*.<sites domain>`, to each
platform UI's host in the platform zone, and never to the application API hosts under `apps-api`,
which reach the app server directly.

- `<app>--<ui>.<org>.<sites domain>` serves `sites/<that host>/` from the sites storage account, the
  file a path names or the site's `index.html` for a route of the single-page application; hashed
  assets under `assets/` are cached for a year, everything else for a minute.
- `<tenant>--<app>--<ui>.<org>.<sites domain>` serves the same application site.
- `<org>.<sites domain>` redirects to the portal's login for the organization.
- A host `UI_HOSTS` names, `portal.<platform zone>` or `console.<platform zone>` (`dev-portal` and
  `dev-console` on the development server), serves `sites/<that host>/` the same way; the platform's
  UIs are never under the sites domain, so no site is same-site with them.

`worker.js` is deployed as it is by `deployment/terraform/azure/modules/cloudflare-sites`, which also
sets its bindings, among them the read-only SAS on the private sites container that signs every read. Run its tests with `bun test` here; CI runs them with the JS workspace tests.
