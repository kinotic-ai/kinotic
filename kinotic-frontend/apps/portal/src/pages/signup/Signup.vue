<template>
  <AuthPageShell>
    <div v-if="!submitted" class="login-form">
      <h2 class="signup-title">Create your organization</h2>

      <!-- Social signup — IdP returns identity, then user picks an org name on /register -->
      <div v-if="providers.length > 0" class="w-full max-w-80 flex flex-col gap-3 mt-2 mb-6">
        <SocialAuthButton
          v-for="provider in providers"
          :key="provider"
          :provider="provider"
          :action="apiUrl('/api/auth/org/signup/social/start/' + provider)"
          intent="sign-up"
        />
        <div class="login-divider"><span>or with email</span></div>
      </div>

      <div class="login-form__step">
        <div class="login-field">
          <InputText
            ref="displayName"
            v-model="request.displayName"
            class="login-input"
            placeholder="Your name"
            @keyup.enter="focusNext('email')"
          />
        </div>

        <div class="login-field">
          <InputText
            ref="email"
            v-model="request.email"
            class="login-input"
            placeholder="Your email"
            type="email"
            @keyup.enter="handleSubmit"
          />
        </div>

        <Button
          label="Sign Up"
          class="login-submit"
          :loading="loading"
          @click="handleSubmit"
        />
      </div>

      <div class="text-center mt-4 text-sm">
        Already have an account? <router-link to="/login" class="login-link">Sign in</router-link>
      </div>
    </div>

    <div v-else class="login-form">
      <div class="text-center pt-4 pb-2">
        <span class="inline-flex items-center justify-center w-18 h-18 rounded-full mb-6 bg-[color-mix(in_srgb,var(--p-primary-color)_14%,transparent)]">
          <span class="pi pi-envelope text-[2rem] text-primary"></span>
        </span>
        <h2 class="text-2xl font-semibold mb-3 text-center">Check your email</h2>
        <p class="my-1 leading-normal">
          We've sent a verification link to
        </p>
        <p class="mt-1 mb-4 font-semibold break-all">{{ request.email }}</p>
        <p class="my-1 leading-normal">
          Click the link to name your organization and finish setting up.
        </p>
        <p class="mt-4 leading-normal text-muted-color text-sm">
          The link expires in 24 hours.
        </p>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { ref, onMounted, type Ref } from 'vue'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import type { SignUpRequest } from '@kinotic-ai/os-api'

import { apiUrl } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'

const toast = useToast()

const request = ref<SignUpRequest>({
  email: '',
  displayName: '',
})
const loading = ref(false)
const submitted = ref(false)
const providers = ref<string[]>([])

const displayName = ref<InstanceType<typeof InputText>>()
const email = ref<InstanceType<typeof InputText>>()

onMounted(async () => {
  try {
    const res = await fetch(apiUrl('/api/auth/org/login/providers'), { credentials: 'same-origin' })
    if (res.ok) {
      const data = await res.json()
      if (Array.isArray(data)) providers.value = data
    }
  } catch {
    // No social providers configured — silent; the email/password form still works.
  }
})

function focusNext(refName: string) {
  const refs: Record<string, Ref<InstanceType<typeof InputText> | undefined>> = {
    displayName,
    email,
  }
  const el = refs[refName]?.value as any
  if (el?.$el) {
    el.$el.querySelector('input')?.focus()
  } else if (el?.focus) {
    el.focus()
  }
}

async function handleSubmit() {
  request.value.email = request.value.email.trim()
  request.value.displayName = request.value.displayName.trim()

  if (!request.value.email || !request.value.email.includes('@')) {
    displayAlert('Please enter a valid email address')
    return
  }
  if (!request.value.displayName) {
    displayAlert('Your name is required')
    return
  }

  loading.value = true
  try {
    const response = await fetch(apiUrl('/api/auth/org/signup'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request.value),
    })

    const data = await response.json()

    if (!response.ok) {
      displayAlert(data.error || 'Sign-up failed')
      return
    }

    submitted.value = true
  } catch (error: unknown) {
    displayAlert(error instanceof Error ? error.message : 'Sign-up failed')
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
