<script setup lang="ts">
definePageMeta({ layout: 'home', header: false, footer: false })

useHead({ titleTemplate: '%s' })
useSeoMeta({
  title: 'Kinotic — Prototype to production',
  description: 'Linux abstracted the hardware. Kinotic OS abstracts the cloud, so humans and AI can '
    + 'build enterprise software at internet scale.',
})

let observer: IntersectionObserver | undefined

onMounted(() => {
  const targets = document.querySelectorAll('[data-reveal]')

  // Without an observer the reveal targets would stay at opacity 0 forever, so
  // show everything rather than render a blank page.
  if (!('IntersectionObserver' in window)) {
    targets.forEach(el => el.classList.add('in'))
    return
  }

  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      entry.target.classList.add('in')
      observer?.unobserve(entry.target)
    }
  }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' })

  targets.forEach(el => observer!.observe(el))
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <HomeHeroComponent />
  <HomeEnterpriseComponent />
  <HomeApiComponent />
  <HomeFeaturesComponent />
  <HomeBuiltForComponent />
  <HomeTrustComponent />
  <HomeUseCasesComponent />
  <HomeCtaComponent />
</template>
