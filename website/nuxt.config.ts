// The docs moved: the introduction and quick start now live under Getting Started. These keep the
// old URLs working (each is prerendered below so the static site emits a redirect page for it).
const docsRedirects: Record<string, string> = {
  '/apps/introduction': '/getting-started/introduction',
  '/apps/quick-start': '/getting-started/quick-start',
}

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
  // The agent-discovery AI policy gives each AI crawler its own `Allow: /` group, which overrides the
  // /test-results/ Disallow for those crawlers, and it declares `ai-train=yes` in robots.txt.
  agentDiscovery: {
    robots: { aiPolicy: false },
  },
  routeRules: Object.fromEntries(
    Object.entries(docsRedirects).map(([from, to]) => [from, { redirect: to }]),
  ),
  // /V2, /V3 and /vc are unlinked pages shared by URL, so prerendering never discovers them
  // by crawling — without this, `nuxt generate` emits no page for them and the deployed URLs 404.
  // Their own `noindex` meta tag keeps them out of search results and out of /sitemap.xml.
  nitro: {
    prerender: {
      routes: ['/V2', '/V3', '/vc', ...Object.keys(docsRedirects)],
    },
  },
  // The docus sitemap lists content pages only; these are the indexable pages in app/pages.
  sitemap: {
    urls: ['/', '/privacy', '/terms'],
  },
  // The only images are the SVG header logos, which IPX has nothing to optimize. Prerendering an
  // /_ipx/ route never completes with @nuxt/image 2.2 (ipx 4) on Nitro 2, which ends `nuxt generate`
  // early without writing /sitemap.xml, so images are served as-is.
  image: {
    provider: 'none',
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
