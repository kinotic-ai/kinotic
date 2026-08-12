<template>
  <AuthPageShell>
    <div class="login-form">
      <!-- Password form -->
      <div class="login-form__step">
        <div class="text-center mb-6">
          <span class="inline-flex items-center justify-center w-18 h-18 rounded-full mb-6 bg-[color-mix(in_srgb,var(--p-primary-color)_14%,transparent)]">
            <span class="pi pi-shield text-[2rem] text-primary"></span>
          </span>
          <h2 class="text-2xl font-semibold mb-2 text-center leading-tight">Email verified</h2>
          <p class="mt-1 mb-6 leading-normal text-muted-color text-center">Name your organization and set a password to finish.</p>
        </div>

        <div class="login-field">
          <InputText
            ref="orgNameInput"
            v-model="request.orgName"
            class="login-input"
            placeholder="Organization name"
            @keyup.enter="focusPassword"
          />
        </div>

        <SetPasswordFields
          ref="passwordFields"
          v-model:password="request.password"
          v-model:confirm="confirmPassword"
          @submit="handleSubmit"
        />

        <Button
          label="Create account"
          class="login-submit"
          :loading="loading"
          :disabled="!canSubmit"
          @click="handleSubmit"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'
import type { SignUpCompleteRequest } from '@kinotic-ai/os-api'

import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SetPasswordFields from '@/components/SetPasswordFields.vue'
import { apiUrl, readAuthError } from '@kinotic-ai/frontend-common'
import { CONTINUUM_UI } from '@/IContinuumUI'
import { KinoticStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'

const toast = useToast()
const userState: IUserState = KinoticStates.getUserState()
const route = useRoute()

const request = ref<SignUpCompleteRequest>({
  token: '',
  orgName: '',
  password: '',
})
const confirmPassword = ref('')
const loading = ref(false)

const passwordFields = ref<InstanceType<typeof SetPasswordFields>>()

const canSubmit = computed<boolean>(() => {
  return !!request.value.orgName
      && !!request.value.password
      && !!confirmPassword.value
      && request.value.password === confirmPassword.value
})

onMounted(() => {
  request.value.token = (route.query.token as string) || ''
  if (!request.value.token) {
    displayAlert('No verification token provided.')
  }
})

function focusPassword() {
  passwordFields.value?.focus()
}

async function handleSubmit() {
  if (!request.value.token) {
    displayAlert('No verification token provided.')
    return
  }
  request.value.orgName = request.value.orgName.trim()
  if (!request.value.orgName) {
    displayAlert('Organization name is required')
    return
  }
  if (!request.value.password) {
    displayAlert('Password is required')
    return
  }
  if (request.value.password !== confirmPassword.value) {
    displayAlert('Passwords do not match')
    return
  }

  loading.value = true
  try {
    const response = await fetch(apiUrl('/api/auth/org/signup/complete'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(request.value),
    })

    if (!response.ok) {
      displayAlert(await readAuthError(response, 'Account creation failed'))
      return
    }

    // The org, admin user, and browser session are created; connect with it and go to the app.
    await userState.login()
    await CONTINUUM_UI.navigate('/applications')
  } catch (error: unknown) {
    displayAlert(error instanceof Error ? error.message : 'Account creation failed')
  } finally {
    loading.value = false
  }
}


function displayAlert(text: string) {
  toast.add({
    severity: 'error',
    summary: 'Error',
    detail: text,
    life: 10000
  })
}
</script>
