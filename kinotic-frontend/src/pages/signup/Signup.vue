<template>
  <AuthPageShell>
    <div v-if="!submitted" class="login-form">
      <h2 class="signup-title">Create your organization</h2>

      <!-- Social signup — IdP returns identity, then user picks an org name on /register -->
      <div v-if="providers.length > 0" class="signup-providers">
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

      <div class="signup-footer-link">
        Already have an account? <router-link to="/login" class="login-link">Sign in</router-link>
      </div>
    </div>

    <div v-else class="login-form">
      <div class="signup-success">
        <span class="signup-success__icon-wrap">
          <span class="pi pi-envelope signup-success__icon"></span>
        </span>
        <h2 class="signup-success__title">Check your email</h2>
        <p class="signup-success__text">
          We've sent a verification link to
        </p>
        <p class="signup-success__email">{{ request.email }}</p>
        <p class="signup-success__text">
          Click the link to name your organization and finish setting up.
        </p>
        <p class="signup-success__text signup-success__text--muted">
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

import { apiUrl } from '@/util/helpers'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
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

<style scoped>
.signup-footer-link {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.875rem;
}

.signup-success {
  text-align: center;
  padding: 1rem 0 0.5rem;
}

.signup-success__icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
  margin-bottom: 1.5rem;
}

.signup-success__icon {
  font-size: 2rem;
  color: var(--p-primary-color);
}

.signup-success__title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  text-align: center;
}

.signup-success__text {
  margin: 0.25rem 0;
  line-height: 1.5;
}

.signup-success__email {
  margin: 0.25rem 0 1rem;
  font-weight: 600;
  word-break: break-all;
}

.signup-success__text--muted {
  margin-top: 1rem;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

.signup-providers {
  width: min(100%, 20rem);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0.5rem 0 1.5rem;
}
</style>
