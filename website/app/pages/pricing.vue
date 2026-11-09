<script setup lang="ts">
// An unlinked page for reviewing pricing, shared by URL. nuxt.config.ts lists /pricing under
// nitro.prerender.routes so `nuxt generate` emits it without a link to crawl.
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s' })
useSeoMeta({
  title: 'Kinotic — Pricing',
  description: 'Free for small companies. Above $1M in annual revenue, your price is set by your company\'s '
    + 'annual revenue, and every plan includes every Enterprise feature.',
  robots: 'noindex, nofollow',
})

useMarketingMotion()
</script>

<template>
  <PricingPlansComponent />
  <PricingCompareComponent />
  <PricingFaqComponent />
</template>
