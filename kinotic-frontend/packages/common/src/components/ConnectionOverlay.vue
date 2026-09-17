<template>
  <Dialog
    :visible="showing"
    modal
    :closable="false"
    :draggable="false"
    :close-on-escape="false"
    header="Can't reach the server"
    :style="{ width: '26rem', maxWidth: '90vw' }"
  >
    <div class="flex items-center gap-3">
      <ProgressSpinner style="width: 2rem; height: 2rem" stroke-width="6" aria-label="Reconnecting" />
      <p class="text-sm text-muted-color">Waiting for it to answer — this clears as soon as it does.</p>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import { onBeforeUnmount, ref, watch } from 'vue'
import { CONNECTION_STATE } from '../session/connectionState'

/**
 * Covers the app while it cannot reach the server, so nothing is clicked that would only fail, and
 * uncovers it as soon as the client is talking to the server again.
 */

// The client retries a dropped connection after its own delay plus jitter, so an outage it recovers from
// quickly would open and shut the dialog again without this
const SETTLE_MS = 1000

const showing = ref(false)
let settle: ReturnType<typeof setTimeout> | null = null

// immediate, since the session probe can find the server unreachable before this mounts
watch(() => CONNECTION_STATE.reachable, (reachable: boolean) => {
  if (settle !== null) {
    clearTimeout(settle)
    settle = null
  }
  if (reachable) {
    showing.value = false
  } else {
    settle = setTimeout(() => {
      settle = null
      showing.value = true
    }, SETTLE_MS)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (settle !== null) {
    clearTimeout(settle)
  }
})
</script>
