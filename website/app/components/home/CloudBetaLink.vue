<script setup lang="ts">
import { trackClarityEvent } from '~/utils/clarity'
import { trackGoogleAnalyticsEvent } from '~/utils/googleAnalytics'

/**
 * The Kinotic Cloud beta call to action: a button that opens sign-up on the Kinotic Cloud portal and
 * records the click, with where on the site it was made, as a `cloud_beta_cta_clicked` analytics event.
 */
const props = defineProps<{
  /** Where the button sits, sent with the click event so each placement's clicks can be compared. */
  placement: 'hero' | 'cta' | 'docs'
}>()

const SIGNUP_URL = 'https://portal.kinotic.ai/signup'
const EVENT_NAME = 'cloud_beta_cta_clicked'

function trackClick() {
  trackGoogleAnalyticsEvent(EVENT_NAME, { placement: props.placement })
  trackClarityEvent(EVENT_NAME, { cloud_beta_cta_placement: props.placement })
}
</script>

<template>
  <a :href="SIGNUP_URL" class="k-btn k-btn--mint cloud-beta-link" @click="trackClick">
    Get started free ↗
  </a>
</template>

<style scoped>
.cloud-beta-link:focus-visible {
  outline: 2px solid var(--color-k-mint);
  outline-offset: 3px;
}
</style>
