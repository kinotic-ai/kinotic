<template>
  <AuthPageShell :art="loginBackgroundArt" :show-theme-toggle="false">
    <div class="login-form">
      <div v-if="!userCode" class="text-center py-8">
        <span class="pi pi-exclamation-triangle text-5xl text-primary-500 mb-4"></span>
        <h2 class="signup-title">Missing device code</h2>
        <p class="login-form__subtitle">
          Open the verification link shown in your command line.
        </p>
      </div>

      <div v-else-if="approved" class="text-center py-8">
        <span class="pi pi-check-circle text-5xl text-primary-500 mb-4"></span>
        <h2 class="signup-title">Device approved</h2>
        <p class="login-form__subtitle">
          You can close this tab and return to your command line.
        </p>
      </div>

      <div v-else class="login-form__step">
        <h2 class="signup-title">Authorize the CLI</h2>
        <p class="login-form__subtitle">
          Confirm this code matches the one shown in your command line.
        </p>
        <div class="text-[2rem] font-bold tracking-[0.3em] text-center my-6">{{ userCode }}</div>
        <Button
          label="Approve"
          class="login-submit"
          :loading="loading"
          @click="handleApprove"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

import loginPageLeft from '@/assets/login-page-left.svg'
import { apiUrl, AuthPageShell, readAuthError } from '@kinotic-ai/frontend-common'

/**
 * The RFC 8628 device-verification page. The CLI sends the user here
 * (`/device?user_code=<code>`); the signed-in user confirms the code, which binds their
 * account to the pending CLI authorization grant.
 */
const loading = ref(false)
const approved = ref(false)

const loginBackgroundArt = loginPageLeft
const toast = useToast()
const route = useRoute()

const userCode = computed<string | null>(() => {
  const code = route.query.user_code
  return typeof code === 'string' && code.length > 0 ? code : null
})

async function handleApprove() {
  const userCodeValue = userCode.value
  if (!userCodeValue) return
  loading.value = true
  try {
    const res = await fetch(apiUrl('/api/auth/oauth/device/approve'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ userCode: userCodeValue })
    })
    if (res.ok) {
      approved.value = true
    } else {
      displayError(await readAuthError(res, 'Could not approve the device'))
    }
  } catch (err) {
    displayError(err instanceof Error ? err.message : 'Could not approve the device')
  } finally {
    loading.value = false
  }
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>

