<template>
  <AuthPageShell>
    <div class="login-form">
      <!-- Password form -->
      <div class="login-form__step">
        <div class="verify-header">
          <span class="verify-icon-wrap verify-icon-wrap--primary">
            <span class="pi pi-shield verify-header__icon"></span>
          </span>
          <h2 class="verify-title">Email verified</h2>
          <p class="verify-text">Name your organization and set a password to finish.</p>
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
import { apiUrl } from '@kinotic-ai/frontend-common'
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
      displayAlert(await readError(response, 'Account creation failed'))
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

async function readError(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.json()
    return body?.error ?? fallback
  } catch {
    return fallback
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

<style scoped>
.verify-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 999px;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap--primary {
  background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
}

.verify-header__icon {
  font-size: 2rem;
}

.verify-icon-wrap--primary .verify-header__icon {
  color: var(--p-primary-color);
}

.verify-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.5rem;
  text-align: center;
  line-height: 1.25;
}

.verify-text {
  margin: 0.25rem 0 1.5rem;
  line-height: 1.5;
  color: var(--p-text-muted-color);
  text-align: center;
}
</style>
