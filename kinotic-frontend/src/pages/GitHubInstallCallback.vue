<template>
  <div class="flex h-screen w-screen items-center justify-center">
    <div v-if="state === 'working'" class="flex items-center gap-3 text-sm text-surface-500">
      <i class="pi pi-spin pi-spinner"></i>
      <span>Finishing GitHub install…</span>
    </div>
    <div v-else-if="state === 'error'" class="max-w-md p-6 text-sm">
      <h2 class="mb-2 text-lg font-semibold text-red-600">Couldn't finish linking GitHub</h2>
      <p class="mb-4 text-surface-600">{{ errorMessage }}</p>
      <Button label="Back to GitHub settings" severity="secondary" @click="goToSettings" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Kinotic } from '@kinotic-ai/core'
import Button from 'primevue/button'

const route = useRoute()
const router = useRouter()

const state = ref<'working' | 'error'>('working')
const errorMessage = ref('')

const SETTINGS_PATH = '/integrations/github'

function goToSettings() {
  router.replace(SETTINGS_PATH)
}

onMounted(async () => {
  const installationIdParam = route.query.installation_id
  const stateParam = route.query.state

  const installationId = typeof installationIdParam === 'string' ? Number.parseInt(installationIdParam, 10) : NaN
  const stateValue = typeof stateParam === 'string' ? stateParam : null

  // GitHub redirects the whole tab here after the install. Finalise the round-trip
  // against the platform and bounce to the SPA-supplied returnTo.
  if (!Number.isFinite(installationId) || !stateValue) {
    state.value = 'error'
    errorMessage.value = 'GitHub redirect was missing the expected parameters.'
    return
  }

  try {
    const result = await Kinotic.githubAppInstallations.completeInstall(installationId, stateValue)
    router.replace(result.returnTo ?? SETTINGS_PATH)
  } catch (e) {
    state.value = 'error'
    errorMessage.value = (e as Error)?.message ?? 'Unknown error completing GitHub install.'
  }
})
</script>
