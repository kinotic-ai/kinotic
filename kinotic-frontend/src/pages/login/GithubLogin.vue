<template>
  <AuthPageShell>
    <div class="login-form">
      <div class="login-providers">
        <SocialAuthButton
          provider="github"
          :action="apiUrl('/api/auth/org/login/social/start/github')"
          intent="sign-in"
        />
      </div>

      <div class="login-signup-link">
        <span>New to Kinotic?</span>
        <router-link to="/signup">Create an organization</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'

import { apiUrl } from '@/util/helpers'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import SocialAuthButton from '@/components/SocialAuthButton.vue'

const toast = useToast()
const route = useRoute()
const router = useRouter()

onMounted(() => {
  consumeUrlError()
})

/** Every auth flow (login, signup, invite) redirects here with ?error=<code> on failure. */
function consumeUrlError() {
  const error = (route.query.error as string | undefined)
      ?? new URLSearchParams(window.location.search).get('error')
  if (!error) return

  displayError(errorCodeToMessage(error))

  // Clean the query string so a refresh doesn't replay the error.
  const newQuery = { ...route.query }
  delete newQuery.error
  router.replace({ query: newQuery })
}

function errorCodeToMessage(code: string): string {
  switch (code) {
    case 'no_account':         return 'No account is linked to that identity. Please sign up first.'
    case 'account_exists':     return 'An account already exists for this identity. Please sign in instead.'
    case 'email_not_verified': return 'Your identity provider has not verified your email address.'
    case 'state_mismatch':     return 'Login session expired or was tampered with. Please try again.'
    case 'access_denied':      return 'You declined to authorize Kinotic. Please try again to continue.'
    case 'signup_failed':      return 'Sign-up failed. Please try again.'
    case 'exchange_failed':
    case 'provisioning_failed':
    case 'lookup_failed':
    case 'invalid_callback':
    case 'invalid_token':      return 'Sign-in failed. Please try again.'
    default:                   return `Sign-in failed: ${code}`
  }
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>
