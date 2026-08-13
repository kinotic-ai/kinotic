<template>
  <div>
    <div class="mb-2 text-sm text-surface-500">Account</div>
    <h1 class="mb-6 text-2xl font-semibold text-surface-950 dark:text-surface-0">Profile</h1>

    <section class="flex max-w-[560px] flex-col gap-5">
      <div class="flex flex-col gap-1">
        <label for="profile-email" class="text-sm font-medium">Email</label>
        <InputText id="profile-email" :model-value="email" disabled />
        <small class="text-muted-color">Identifies your account within this organization.</small>
      </div>

      <div class="flex flex-col gap-1">
        <label for="profile-display-name" class="text-sm font-medium">Display name</label>
        <InputText id="profile-display-name" v-model="displayName" :disabled="loading"
                   autocomplete="name" @keyup.enter="save" />
        <small class="text-muted-color">Shown wherever you appear in the platform.</small>
      </div>

      <div>
        <Button label="Save changes" :loading="saving" :disabled="!canSave" @click="save" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
