<template>
  <AuthPageShell :art="loginBackgroundArt" :show-theme-toggle="false">
    <div class="login-form">
      <div v-if="!requestId || failed" class="consent-message">
        <span class="pi pi-exclamation-triangle consent-message__icon"></span>
        <h2 class="signup-title">Authorization request unavailable</h2>
        <p class="login-form__subtitle">
          {{ failed || 'Open the authorization link from your application again.' }}
        </p>
      </div>

      <div v-else-if="pending" class="login-form__step">
        <h2 class="signup-title">Authorize {{ pending.clientName }}</h2>
        <p class="login-form__subtitle">
          Verified as <strong class="consent-host">{{ clientHost }}</strong>
        </p>
        <p class="login-form__subtitle">
          {{ pending.clientName }} is requesting access to Kinotic OS as your account.
          It will be able to call the MCP tools your account can call.
        </p>
        <Button
          label="Approve"
          class="login-submit"
          :loading="deciding"
          @click="decide(true)"
        />
        <Button
          label="Deny"
          class="login-submit consent-deny"
          severity="secondary"
          :loading="deciding"
          @click="decide(false)"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Kinotic } from '@kinotic-ai/core'
import Button from 'primevue/button'

import loginPageLeft from '@/assets/login-page-left.svg'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'

interface PendingOAuthAuthorization {
  clientName: string
  clientId: string
  scope: string | null
}

interface OAuthApprovalProxy {
  describe(requestId: string): Promise<PendingOAuthAuthorization>
  approve(requestId: string): Promise<string>
  deny(requestId: string): Promise<string>
}

// typed locally until the installed @kinotic-ai/os-api ships the oauthApproval extension
const oauthApproval = (Kinotic as unknown as { oauthApproval: OAuthApprovalProxy }).oauthApproval

/**
 * The OAuth 2.1 consent page. The gateway's authorize endpoint sends the browser here
 * (`/oauth/consent?request_id=<id>`); the signed-in user approves or denies, and either
 * decision returns the client's redirect URL this page navigates to.
 */
const pending = ref<PendingOAuthAuthorization | null>(null)
const failed = ref<string | null>(null)
const deciding = ref(false)

const loginBackgroundArt = loginPageLeft
const route = useRoute()

const requestId = computed<string | null>(() => {
  const id = route.query.request_id
  return typeof id === 'string' && id.length > 0 ? id : null
})

// the client had to serve its metadata document from this host, so unlike clientName it is a
// claim the client could not simply assert
const clientHost = computed<string>(() => {
  try {
    return new URL(pending.value!.clientId).host
  } catch {
    return pending.value?.clientId ?? ''
  }
})

onMounted(async () => {
  if (!requestId.value) return
  try {
    pending.value = await oauthApproval.describe(requestId.value)
  } catch (err) {
    failed.value = err instanceof Error ? err.message : 'Could not load the authorization request'
  }
})

async function decide(approve: boolean) {
  const id = requestId.value
  if (!id) return
  deciding.value = true
  try {
    const redirectUrl = approve
      ? await oauthApproval.approve(id)
      : await oauthApproval.deny(id)
    window.location.href = redirectUrl
  } catch (err) {
    failed.value = err instanceof Error ? err.message : 'Could not complete the authorization'
    deciding.value = false
  }
}
</script>

<style scoped>
.consent-message {
  text-align: center;
  padding: 2rem 0;
}

.consent-message__icon {
  font-size: 3rem;
  color: var(--p-primary-500);
  margin-bottom: 1rem;
}

.consent-host {
  font-family: monospace;
}

.consent-deny {
  margin-top: 0.75rem;
}
</style>
