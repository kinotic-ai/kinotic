<template>
  <div class="p-4">
    <h1 class="text-xl font-semibold mb-4">GitHub</h1>

    <div v-if="loading" class="flex items-center gap-2">
      <i class="pi pi-spin pi-spinner"></i>
      <span>Loading…</span>
    </div>

    <div v-else-if="error" class="text-red-600">{{ error }}</div>

    <div v-else-if="installation" class="space-y-3">
      <p>
        GitHub linked as
        <strong>{{ installation.accountLogin }}</strong>
        ({{ installation.accountType }})
      </p>
      <p v-if="installation.suspendedAt" class="text-amber-700">
        Installation suspended on {{ installation.suspendedAt }}.
      </p>
      <Button label="Unlink GitHub" severity="danger" @click="unlink" :disabled="busy" />
    </div>

    <div v-else class="space-y-3">
      <p>GitHub is not linked for this organization.</p>
      <Button label="Link GitHub" severity="primary" @click="link" :disabled="busy" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  GITHUB_APP_INSTALLATION_SERVICE,
  type GitHubAppInstallation,
} from '@/services/GitHubAppInstallationService'

const installation = ref<GitHubAppInstallation | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    installation.value = await GITHUB_APP_INSTALLATION_SERVICE.findForCurrentOrg()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function link() {
  busy.value = true
  error.value = null
  try {
    const { url } = await GITHUB_APP_INSTALLATION_SERVICE.startInstall()
    window.location.href = url
  } catch (e) {
    error.value = (e as Error).message
    busy.value = false
  }
}

async function unlink() {
  if (!installation.value) return
  busy.value = true
  try {
    await GITHUB_APP_INSTALLATION_SERVICE.deleteById(installation.value.id)
    installation.value = null
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
