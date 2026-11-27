<script setup lang="ts">
import DocusApp from 'docus/app/app.vue'
import { CONSENT_COOKIE } from '~/utils/consent'

const consent = useCookieConsent()

// Every prerendered page carries the open banner. This inline script runs before first paint
// and hides it for a visitor with a saved choice, until loadSavedChoice has closed it.
useHead({
  script: [{
    key: 'cookie-consent-saved',
    innerHTML: `if(document.cookie.split('; ').some(function(c){return c.indexOf(${JSON.stringify(`${CONSENT_COOKIE}=`)})===0}))`
      + `document.documentElement.classList.add('k-consent-saved')`,
  }],
})

onMounted(async () => {
  consent.loadSavedChoice()
  // Waits for the closed banner to leave the DOM so lifting the class cannot flash it.
  await nextTick()
  document.documentElement.classList.remove('k-consent-saved')
})
</script>

<template>
  <DocusApp />
  <CookieConsent
    v-if="consent.visible.value"
    data-cookie-consent
    :current="consent.current.value"
    @save="consent.save"
  />
</template>

<style>
html.k-consent-saved [data-cookie-consent] {
  display: none;
}
</style>
