<script setup lang="ts">
// A second unlinked preview, sitting beside /V2 so the two directions for the API
// section can be compared side by side. Everything else on the page is the same.
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
  <HomeApiConversationComponent />
  <HomeFeaturesComponent />
  <HomeBuiltForComponent />
  <HomeTrustComponent />
  <HomeUseCasesComponent />
  <HomeCtaComponent />
</template>
