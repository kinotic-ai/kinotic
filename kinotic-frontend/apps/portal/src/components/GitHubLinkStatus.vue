<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import type { GitHubAppInstallation } from '@kinotic-ai/management-api'
import Button from 'primevue/button'
import { GitBranch, Unplug } from '@lucide/vue'
import { isDark as darkMode, TINTS } from '@kinotic-ai/frontend-common'
import FeatureEmptyState from '@/components/FeatureEmptyState.vue'
import githubLogo from '@/assets/github-icon.svg'
import kinoticLogo from '@/assets/header-logo.svg'

const props = defineProps<{
  /** Route the GitHub install round-trip returns to after linking. */
  returnTo: string
}>()

const installations = Kinotic.githubAppInstallations
const isDark = darkMode

// Widths of the placeholder repository rows in the not-linked preview
const REPO_ROWS = ['46%', '34%', '52%']

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
  if (!installation.value) return
  busy.value = true
  error.value = null
  try {
    await installations.unlink()
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
  <FeatureEmptyState
    v-if="!loading && !installation"
    badge="not-connected"
    :icon="GitBranch"
    :tint="TINTS.surface"
    title="Connect GitHub"
    description="Link your GitHub account to your organization so every project is backed by a repository, and a push is all it takes to deploy."
    :points="[
      'Each new project gets a repository under the linked account',
      'Pushing to a project\'s default branch deploys it',
      'Kinotic reaches only the repositories its GitHub App is installed on'
    ]"
  >
    <template #preview>
      <div class="flex items-center justify-center gap-3 pb-6 pt-2">
        <span class="flex h-12 w-12 items-center justify-center rounded-xl border border-surface-200 bg-surface-0 shadow-sm dark:border-surface-700 dark:bg-surface-900">
          <img :src="githubLogo" alt="" class="h-6 w-6 dark:invert" />
        </span>
        <span class="h-px w-10 border-t-2 border-dashed border-surface-300 dark:border-surface-600" />
        <span class="flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300">
          <Unplug :size="16" :stroke-width="2" />
        </span>
        <span class="h-px w-10 border-t-2 border-dashed border-surface-300 dark:border-surface-600" />
        <span class="flex h-12 w-12 items-center justify-center rounded-xl bg-surface-950 shadow-sm dark:ring-1 dark:ring-surface-700">
          <img :src="kinoticLogo" alt="" class="h-[18px] w-5" />
        </span>
      </div>
      <div class="rounded-xl border border-surface-200 bg-surface-0 p-4 shadow-sm dark:border-surface-700 dark:bg-surface-900">
        <div class="mb-2 text-xs font-semibold text-surface-950 dark:text-surface-0">Repositories</div>
        <div v-for="width in REPO_ROWS" :key="width" class="flex items-center gap-3 border-t border-surface-100 py-2.5 dark:border-surface-800">
          <GitBranch :size="14" :stroke-width="1.75" class="text-surface-400" />
          <div class="h-2.5 rounded bg-surface-200 dark:bg-surface-700" :style="{ width }" />
          <span class="ml-auto h-2.5 w-10 rounded bg-surface-200 dark:bg-surface-700" />
        </div>
      </div>
    </template>
    <template #actions>
      <Button label="Link GitHub" icon="pi pi-github" :loading="busy" @click="link" />
      <p v-if="error" class="w-full text-sm text-red-600">{{ error }}</p>
    </template>
  </FeatureEmptyState>

  <section v-else class="max-w-[720px] pt-4">
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

          <p v-if="error" class="mt-2 text-sm text-red-600">{{ error }}</p>
        </div>

        <div v-if="installation" class="shrink-0">
          <Button
            label="Unlink"
            severity="danger"
            outlined
            size="small"
            :disabled="busy"
            @click="unlink"
          />
        </div>
      </div>
    </div>
  </section>
</template>
