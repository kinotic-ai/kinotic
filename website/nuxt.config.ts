export default defineNuxtConfig({
  extends: ['docus'],
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
  // /V2 is an unlinked preview shared by URL, so prerendering never discovers it by crawling —
  // without this, `nuxt generate` emits no page for it and the deployed URL 404s. It is kept out
  // of search results by its own `noindex` meta tag; the sitemap covers content pages only.
  nitro: {
    prerender: {
      routes: ['/V2'],
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
