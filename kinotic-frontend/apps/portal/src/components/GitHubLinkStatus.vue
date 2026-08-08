<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import type { GitHubAppInstallation } from '@kinotic-ai/os-api'
import Button from 'primevue/button'
import { isDark as darkMode } from '@kinotic-ai/frontend-common'

const props = defineProps<{
  /** Route the GitHub install round-trip returns to after linking. */
  returnTo: string
}>()

const installations = Kinotic.githubAppInstallations
const isDark = darkMode

const installation = ref<GitHubAppInstallation | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    installation.value = await installations.findForCurrentOrg()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function link(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    const url = await installations.startInstall(props.returnTo)
    window.location.href = url
  } catch (e) {
    error.value = (e as Error).message
    busy.value = false
  }
}

async function unlink(): Promise<void> {
  if (!installation.value?.id) return
  busy.value = true
  error.value = null
  try {
    await installations.deleteById(installation.value.id)
    installation.value = null
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div :class="['rounded-lg border p-4', isDark ? 'border-surface-800 bg-surface-900' : 'border-surface-200 bg-surface-0']">
    <div class="flex items-start gap-3">
      <div :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', isDark ? 'bg-surface-800' : 'bg-surface-100']">
        <i class="pi pi-github text-lg" />
      </div>

      <div class="min-w-0 flex-1">
        <h2 :class="['text-base font-semibold', isDark ? 'text-surface-0' : 'text-surface-950']">GitHub</h2>

        <div v-if="loading" class="mt-1 flex items-center gap-2 text-sm text-surface-500">
          <i class="pi pi-spin pi-spinner" />
          <span>Checking link status…</span>
        </div>

        <template v-else-if="installation">
          <p :class="['mt-1 text-sm', isDark ? 'text-surface-300' : 'text-surface-600']">
            Linked as
            <strong :class="isDark ? 'text-surface-0' : 'text-surface-950'">{{ installation.accountLogin }}</strong>
            ({{ installation.accountType }})
          </p>
          <p v-if="installation.suspendedAt" class="mt-1 text-sm text-amber-600 dark:text-amber-500">
            <i class="pi pi-exclamation-triangle mr-1" />
            Installation suspended.
          </p>
        </template>

        <p v-else :class="['mt-1 text-sm', isDark ? 'text-surface-300' : 'text-surface-600']">
          Not linked. Link your GitHub account to back projects with a repository.
        </p>

        <p v-if="error" class="mt-2 text-sm text-red-600">{{ error }}</p>
      </div>

      <div v-if="!loading" class="shrink-0">
        <Button
          v-if="installation"
          label="Unlink"
          severity="danger"
          outlined
          size="small"
          :disabled="busy"
          @click="unlink"
        />
        <Button
          v-else
          label="Link GitHub"
          severity="primary"
          size="small"
          :disabled="busy"
          @click="link"
        />
      </div>
    </div>
  </div>
</template>
