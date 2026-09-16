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
          {{ pending.clientName }} is requesting access to Kinotic OS as
          <strong>{{ accountLabel }}</strong>, and will be able to call the MCP tools that
          account can call.
        </p>
        <Message v-if="systemAccount" severity="warn" :closable="false" class="consent-warning">
          This is a platform operator account. It is not scoped to an organization, so
          {{ pending.clientName }} would reach the MCP tools of every organization on this
          platform. To authorize it for one organization, sign out and sign in to that
          organization's portal account first.
        </Message>
        <Button
          label="Approve"
          class="login-submit"
          :loading="deciding === 'approve'"
          :disabled="deciding !== null"
          @click="decide('approve')"
        />
        <Button
          label="Deny"
          class="login-submit consent-deny"
          severity="secondary"
          :loading="deciding === 'deny'"
          :disabled="deciding !== null"
          @click="decide('deny')"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Kinotic } from '@kinotic-ai/core'
import { isSystemParticipant } from '@kinotic-ai/management-api'
import type { PendingOAuthAuthorization } from '@kinotic-ai/management-api'
import Button from 'primevue/button'
import Message from 'primevue/message'

import loginPageLeft from '@/assets/login-page-left.svg'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import { USER_STATE } from '@/states/IUserState'

const oauthApproval = Kinotic.oauthApproval

/**
 * The OAuth 2.1 consent page. The gateway's authorize endpoint sends the browser here
 * (`/oauth/consent?request_id=<id>`); the signed-in user approves or denies, and either
 * decision returns the client's redirect URL this page navigates to.
 */
type Decision = 'approve' | 'deny'

const pending = ref<PendingOAuthAuthorization | null>(null)
const failed = ref<string | null>(null)
// which decision is in flight, so only the clicked button shows its spinner
const deciding = ref<Decision | null>(null)

const loginBackgroundArt = loginPageLeft
const route = useRoute()

// the browser session already carries who is signed in, so the account being authorized is
// named without asking the server again. a system-scoped session reaches this page the same
// way an organization one does — the console and the portal share the session cookie
const participant = computed(() => USER_STATE.connectedInfo?.participant ?? null)

const accountLabel = computed<string>(() => participant.value?.metadata?.email ?? 'your account')

const systemAccount = computed<boolean>(() =>
    participant.value !== null && isSystemParticipant(participant.value))

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

async function decide(decision: Decision) {
  const id = requestId.value
  if (!id) return
  deciding.value = decision
  try {
    const redirectUrl = decision === 'approve'
      ? await oauthApproval.approve(id)
      : await oauthApproval.deny(id)
    window.location.href = redirectUrl
  } catch (err) {
    failed.value = err instanceof Error ? err.message : 'Could not complete the authorization'
    deciding.value = null
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

.consent-warning {
  margin: 0.75rem 0 0;
  text-align: left;
}

/* Deny is the secondary action, but .login-submit in auth-pages.css paints the primary fill at
 * a specificity PrimeVue's secondary severity cannot beat. Scoping adds the data-v attribute,
 * which wins, and the surface matches the social buttons on the other auth pages. */
.consent-deny.p-button {
  margin-top: 0.75rem;
  background: var(--lp-provider-bg);
  border: 1px solid var(--lp-provider-border);
  color: var(--lp-provider-color);
}

.consent-deny.p-button:hover {
  background: var(--lp-provider-bg-hover);
  border-color: var(--lp-provider-border-hover);
}
</style>
