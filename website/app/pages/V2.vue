<script setup lang="ts">
// An unlinked preview of a home page direction, shared by URL while the live
// home page stays as it is. Kept out of the sitemap and robots.txt in
// nuxt.config.ts so it can be handed around without being indexed.
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s' })
useSeoMeta({
  title: 'Kinotic — Prototype to production',
  description: 'Linux abstracted the hardware. Kinotic OS abstracts the cloud, so humans and AI can '
    + 'build enterprise software at internet scale.',
  robots: 'noindex, nofollow',
})

useMarketingMotion()
</script>

<template>
  <HomeHeroComponent />
  <HomeEnterpriseComponent />
  <HomeApiStoryComponent />
  <HomeFeaturesComponent />
  <HomeBuiltForComponent />
  <HomeTrustComponent />
  <HomeUseCasesComponent />
  <HomeCtaComponent />
</template>
