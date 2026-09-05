<template>
  <div class="max-w-[860px]">
    <PageHeader title="Profile" description="How you appear to everyone else in Kinotic." />

    <div class="flex flex-col gap-6">
      <section class="relative overflow-hidden rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="pointer-events-none absolute -right-20 -top-28 h-72 w-72 rounded-full bg-primary-500/10 blur-[90px] dark:bg-primary-500/20"
             aria-hidden="true" />

        <div class="relative flex flex-wrap items-center gap-5 px-6 py-6">
          <Skeleton v-if="loading" shape="circle" size="4.5rem" />
          <div v-else aria-hidden="true"
               class="grid h-18 w-18 shrink-0 place-items-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600
                      text-2xl font-semibold text-white shadow-lg shadow-primary-500/30 ring-4 ring-primary-500/10">
            {{ initials || '?' }}
          </div>

          <div class="flex min-w-0 flex-1 flex-col gap-1.5">
            <Skeleton v-if="loading" height="1.75rem" width="12rem" />
            <h2 v-else class="truncate text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0">
              {{ savedDisplayName || 'Unnamed user' }}
            </h2>

            <Skeleton v-if="loading" height="1rem" width="16rem" />
            <span v-else class="truncate text-sm text-muted-color">{{ email }}</span>
          </div>

          <Tag v-if="!loading" :value="statusLabel" :severity="statusSeverity(statusLabel)" rounded />
        </div>

        <dl class="relative grid gap-5 border-t border-surface-200 px-6 py-5 sm:grid-cols-3 dark:border-surface-700">
          <div v-for="detail in details" :key="detail.label" class="flex min-w-0 flex-col gap-1.5">
            <dt class="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-color">
              <i :class="detail.icon" />
              {{ detail.label }}
            </dt>
            <dd class="m-0 min-w-0">
              <Skeleton v-if="loading" height="1rem" width="7rem" />
              <button v-else-if="detail.copyable" type="button"
                      class="group flex w-full min-w-0 items-center gap-2 text-left font-mono text-xs text-surface-700 dark:text-surface-300"
                      :title="detail.value" :aria-label="`Copy ${detail.label.toLowerCase()}`"
                      @click="copy(detail.label, detail.value)">
                <span class="truncate">{{ detail.value }}</span>
                <i :class="[copied === detail.label ? 'pi pi-check text-green-500' : 'pi pi-copy opacity-50 group-hover:opacity-100',
                            'shrink-0 !text-xs transition-opacity']" />
              </button>
              <span v-else class="text-sm">{{ detail.value }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section class="rounded-2xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
        <div class="flex flex-col gap-4 px-6 py-6">
          <div class="flex flex-col gap-1">
            <label for="profile-display-name" class="text-sm font-medium">Display name</label>
            <small class="text-muted-color">Shown wherever you appear in the platform.</small>
          </div>

          <Skeleton v-if="loading" height="2.5rem" />
          <InputText v-else id="profile-display-name" v-model="displayName" :invalid="emptyName"
                     class="max-w-[26rem]" autocomplete="name" maxlength="80"
                     @keyup.enter="save" @keyup.esc="reset" />

          <small v-if="emptyName" class="text-red-500">A display name is required.</small>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-surface-200 px-6 py-4 dark:border-surface-700">
          <span class="flex items-center gap-2 text-xs text-muted-color">
            <template v-if="dirty">
              <span class="h-1.5 w-1.5 rounded-full bg-amber-500" aria-hidden="true" />
              Unsaved changes
            </template>
          </span>

          <div class="flex items-center gap-2">
            <Button v-if="dirty" label="Reset" severity="secondary" text size="small"
                    :disabled="saving" @click="reset" />
            <Button label="Save changes" size="small" :loading="saving" :disabled="!canSave" @click="save" />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

import { AuthType } from '@kinotic-ai/management-api'
import { DatetimeUtil, showErrorToast, statusSeverity } from '@kinotic-ai/frontend-common'
import { PageHeader } from '@kinotic-ai/frontend-common'
import { PROFILE_STATE } from '@/states/IProfileState'

/** One read-only fact about the account, rendered as a column of the identity card's footer. */
interface ProfileDetail {
  label: string
  value: string
  icon: string
  copyable?: boolean
}

/** How long the copy button shows its confirmation before returning to the copy icon. */
const COPIED_FEEDBACK_MILLIS = 2000

const toast = useToast()

const displayName = ref('')
const loading = ref(true)
const saving = ref(false)
const copied = ref<string | null>(null)

let copiedTimer: ReturnType<typeof setTimeout> | null = null

const email = computed(() => PROFILE_STATE.profile?.email ?? '')
const savedDisplayName = computed(() => PROFILE_STATE.profile?.displayName ?? '')
const initials = computed(() => PROFILE_STATE.initials)
const statusLabel = computed(() => PROFILE_STATE.profile?.enabled ? 'Active' : 'Disabled')

const dirty = computed(() => !loading.value && displayName.value.trim() !== savedDisplayName.value)
const emptyName = computed(() => !loading.value && displayName.value.trim().length === 0)
const canSave = computed(() => dirty.value && !emptyName.value)

const details = computed<ProfileDetail[]>(() => [
  { label: 'Sign-in method', value: signInMethod(), icon: 'pi pi-shield' },
  { label: 'Member since', value: DatetimeUtil.formatMonthDayYear(PROFILE_STATE.profile?.created ?? 0) || '—', icon: 'pi pi-calendar' },
  { label: 'User ID', value: PROFILE_STATE.profile?.id ?? '—', icon: 'pi pi-hashtag', copyable: true }
])

onMounted(load)

onBeforeUnmount(() => {
  if (copiedTimer) clearTimeout(copiedTimer)
})

function signInMethod(): string {
  switch (PROFILE_STATE.profile?.authType) {
    case AuthType.LOCAL: return 'Password'
    case AuthType.OIDC:  return 'Single sign-on'
    default:             return '—'
  }
}

async function load() {
  try {
    await PROFILE_STATE.load()
    displayName.value = savedDisplayName.value
  } catch (err) {
    showErrorToast(toast, 'Failed to load your profile', err, { life: 8000 })
  } finally {
    loading.value = false
  }
}

function reset() {
  displayName.value = savedDisplayName.value
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    await PROFILE_STATE.updateDisplayName(displayName.value.trim())
    displayName.value = savedDisplayName.value
    toast.add({ severity: 'success', summary: 'Profile saved', life: 5000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to save your profile', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}

async function copy(label: string, value: string) {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = label
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => { copied.value = null }, COPIED_FEEDBACK_MILLIS)
  } catch (err) {
    showErrorToast(toast, `Failed to copy the ${label.toLowerCase()}`, err)
  }
}
</script>
