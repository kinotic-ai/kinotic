<template>
  <div>
    <div class="mb-2 text-sm text-surface-500">Account</div>
    <h1 class="mb-6 text-2xl font-semibold text-surface-950 dark:text-surface-0">Profile</h1>

    <section class="max-w-[560px]">
      <div class="flex items-center gap-5 border-b border-surface-200 pb-6 dark:border-surface-700">
        <Avatar :label="initials" shape="circle" size="xlarge" />
        <div class="flex min-w-0 flex-col gap-1">
          <span class="truncate text-lg font-semibold text-surface-950 dark:text-surface-0">
            {{ savedDisplayName || 'Unnamed user' }}
          </span>
          <span class="truncate text-sm text-muted-color">{{ email }}</span>
          <span class="text-xs text-muted-color">Profile photos aren't supported yet.</span>
        </div>
      </div>

      <div class="flex flex-col gap-5 pt-6">
        <div class="flex flex-col gap-1">
          <label for="profile-display-name" class="text-sm font-medium">Display name</label>
          <InputText id="profile-display-name" v-model="displayName" :disabled="loading"
                     autocomplete="name" @keyup.enter="save" />
          <small class="text-muted-color">Shown wherever you appear in the platform.</small>
        </div>

        <div class="flex flex-col gap-1">
          <label for="profile-email" class="text-sm font-medium">Email</label>
          <InputText id="profile-email" :model-value="email" disabled />
          <small class="text-muted-color">Identifies your account within this organization.</small>
        </div>

        <div>
          <Button label="Save changes" :loading="saving" :disabled="!canSave" @click="save" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { showErrorToast } from '@kinotic-ai/frontend-common'

const toast = useToast()

const email = ref('')
const displayName = ref('')
const savedDisplayName = ref('')
const loading = ref(true)
const saving = ref(false)

const canSave = computed(() => !loading.value
                              && displayName.value.trim().length > 0
                              && displayName.value.trim() !== savedDisplayName.value)

/** Up to two initials, taken from the display name or, when there is none, the email's local part. */
const initials = computed(() => {
  const name = savedDisplayName.value.trim()
  const source = name.length > 0 ? name : (email.value.split('@')[0] ?? '')
  return source.split(/[\s._-]+/)
               .filter(part => part.length > 0)
               .slice(0, 2)
               .map(part => part.charAt(0).toUpperCase())
               .join('')
})

onMounted(load)

async function load() {
  try {
    const profile = await Kinotic.profile.findMyProfile()
    email.value = profile.email
    savedDisplayName.value = profile.displayName ?? ''
    displayName.value = savedDisplayName.value
  } catch (err) {
    showErrorToast(toast, 'Failed to load your profile', err, { life: 8000 })
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    const profile = await Kinotic.profile.updateDisplayName(displayName.value.trim())
    savedDisplayName.value = profile.displayName ?? ''
    displayName.value = savedDisplayName.value
    toast.add({ severity: 'success', summary: 'Profile saved', life: 5000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to save your profile', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}
</script>
