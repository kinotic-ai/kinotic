<script setup lang="ts">
// An unlinked page for reviewing pricing, shared by URL. nuxt.config.ts lists /pricing under
// nitro.prerender.routes so `nuxt generate` emits it without a link to crawl.
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s', htmlAttrs: { class: 'k-smooth-scroll' } })
useSeoMeta({
  title: 'Kinotic — Pricing',
  description: 'Free for small companies. Above $1M in annual revenue, your price is set by your company\'s '
    + 'annual revenue, every plan includes every Enterprise feature, and you can try any paid plan free for three months.',
  robots: 'noindex, nofollow',
})

useMarketingMotion()
</script>

<template>
  <PricingPlansComponent />
  <PricingCompareComponent />
  <PricingDesignPartnerComponent />
  <PricingFaqComponent />
  <PricingCouponComponent />
</template>
