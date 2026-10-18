# sites-worker

The Cloudflare Worker that serves every organization's published sites. It is bound to the site hosts
of the sites domain, `*.<sites domain>`, and never to the application API hosts under `apps-api`,
which reach the app server directly.

- `<app>--<ui>.<org>.<sites domain>` serves `sites/<that host>/` from the sites storage account, the
  file a path names or the site's `index.html` for a route of the single-page application; hashed
  assets under `assets/` are cached for a year, everything else for a minute.
- `<tenant>--<app>--<ui>.<org>.<sites domain>` serves the same application site.
- `<org>.<sites domain>` redirects to the portal's login for the organization.

`worker.js` is deployed as it is by `deployment/terraform/azure/modules/cloudflare-sites`, which also
sets its bindings. Run its tests with `bun test` here; CI runs them with the JS workspace tests.
