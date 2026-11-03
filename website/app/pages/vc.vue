<script setup lang="ts">
// An unlinked page for investors, shared by URL. nuxt.config.ts lists /vc under
// nitro.prerender.routes so `nuxt generate` emits it without a link to crawl.
definePageMeta({
  layout: 'home',
  header: false,
  footer: false,
  headerNav: {
    links: [
      { label: 'About us', to: '#about', external: true },
      { label: 'Partners', to: '#partners', external: true },
      { label: 'Product demo', to: '#demo', external: true },
      { label: 'Investor deck', to: '#deck', external: true },
      { label: 'Roadmap', to: '#roadmap', external: true },
    ],
    cta: { label: 'Contact us', to: '#cta', external: true },
  },
})

useHead({ titleTemplate: '%s', htmlAttrs: { class: 'k-smooth-scroll' } })
useSeoMeta({
  title: 'Kinotic — Our story',
  description: 'Kinotic is the convergence of more than two decades of building software, '
    + 'infrastructure and products together.',
  robots: 'noindex, nofollow',
})

useMarketingMotion()

// The YouTube video open in the modal, or null while it is closed.
const video = ref<string | null>(null)
</script>

<template>
  <VcAboutComponent @play="video = $event" />
  <VcFoundersComponent />
  <VcStoryComponent />
  <VcPartnersComponent />
  <VcDemoComponent @play="video = $event" />
  <VcDeckComponent />
  <VcRoadmapComponent />
  <VcCtaComponent />
  <VcVideoModal :video-id="video" @close="video = null" />
</template>
