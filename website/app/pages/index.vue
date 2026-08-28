<script setup lang="ts">
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s' })
useSeoMeta({
  title: 'Kinotic — Prototype to production',
  description: 'Linux abstracted the hardware. Kinotic OS abstracts the cloud, so humans and AI can '
    + 'build enterprise software at internet scale.',
})

useMarketingMotion()
</script>

<template>
  <HomeHeroComponent />
  <HomeEnterpriseComponent />
  <HomeApiComponent />
  <HomeFeaturesComponent />
  <HomeBuiltForComponent />
  <HomeTrustComponent />
<!--  <HomeUseCasesComponent />-->
  <HomeCtaComponent />
</template>
