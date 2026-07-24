export default defineNuxtConfig({
  extends: ['docus'],
  css: ['~/assets/css/main.css'],
  // Canonical site URL for nuxt-site-config consumers (robots, sitemap, OG images, canonical
  // links). Without it they fall back to http://localhost, which leaks into the deployed
  // robots.txt Sitemap line and OG/canonical URLs.
  site: {
    url: 'https://kinotic.ai',
  },
  // Keep the published Allure test reports out of search indexes. They live at /test-results/ on
  // this same domain but aren't content we want crawled. @nuxtjs/robots is provided by the docus
  // layer; this emits `Disallow: /test-results/` into the generated robots.txt.
  robots: {
    disallow: ['/test-results/'],
  },
  // The reference pages now live under the Kinotic Apps and Kinotic OS sections. These keep the
  // original /reference/* URLs working for anything already linking to them.
  routeRules: {
    '/reference/decorators': { redirect: { to: '/apps/reference/decorators', statusCode: 301 } },
    '/reference/migration-sql-grammar': { redirect: { to: '/apps/reference/migration-sql-grammar', statusCode: 301 } },
    '/reference/abac-expression-language': { redirect: { to: '/apps/reference/abac-expression-language', statusCode: 301 } },
    '/reference/cri-format': { redirect: { to: '/platform/reference/cri-format', statusCode: 301 } },
    '/reference/sdk-packages': { redirect: { to: '/platform/reference/sdk-packages', statusCode: 301 } },
  },
  nitro: {
    prerender: {
      // No page links to the old paths anymore, so the crawler never reaches them. Listing them
      // explicitly makes the static build emit the redirect documents.
      routes: [
        '/reference/decorators',
        '/reference/migration-sql-grammar',
        '/reference/abac-expression-language',
        '/reference/cri-format',
        '/reference/sdk-packages',
      ],
    },
  },
  runtimeConfig: {
    public: {
      clarityProjectId: 'waw4oqkd0y',
    },
  },
  app: {
    head: {
      link: [
        { rel: 'icon', type: 'image/png', href: '/favicon-96x96.png', sizes: '96x96' },
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'shortcut icon', href: '/favicon.ico' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        { rel: 'manifest', href: '/site.webmanifest' },
      ],
      meta: [
        { name: 'apple-mobile-web-app-title', content: 'Kinotic' },
      ],
    },
  },
})
