<template>
  <AuthPageShell>
    <div v-if="phase === 'loading'" class="login-form">
      <div class="login-loading-state">
        <div class="login-spinner login-spinner--small"></div>
      </div>
    </div>

    <div v-else-if="phase === 'invalid'" class="login-form">
      <div class="invite-message">
        <span class="pi pi-exclamation-triangle invite-message__icon"></span>
        <h2 class="signup-title">Invitation unavailable</h2>
        <p class="login-form__subtitle">{{ invalidMessage }}</p>
      </div>
    </div>

    <div v-else-if="phase === 'appAccepted'" class="login-form">
      <div class="invite-message">
        <span class="pi pi-check-circle invite-message__icon invite-message__icon--success"></span>
        <h2 class="signup-title">You're all set</h2>
        <p class="login-form__subtitle">
          Your account for <strong>{{ acceptedApplicationId }}</strong> is ready.
          Sign in through the application to start using it.
        </p>
      </div>
    </div>

    <div v-else class="login-form">
      <h2 class="signup-title">You're invited</h2>
      <p class="login-form__subtitle">
        <strong>{{ details.invitedByName }}</strong> has invited you to join
        <strong>{{ details.applicationId || details.organizationName }}</strong><template v-if="details.applicationId">, provided by {{ details.organizationName }}</template>.
      </p>

      <div v-if="details.providers.length > 0" class="invite-providers">
        <SocialAuthButton
          v-for="provider in details.providers"
          :key="provider.id"
          :provider="provider.provider || provider.name"
          :action="startUrl(provider.id)"
          intent="sign-up"
        />
        <div class="login-divider"><span>or set a password</span></div>
      </div>

      <div class="login-form__step">
        <div class="login-field">
          <InputText
            v-model="displayName"
            class="login-input"
            placeholder="Your name"
            @keyup.enter="focusPassword"
          />
        </div>

        <SetPasswordFields
          ref="passwordFields"
          v-model:password="password"
          v-model:confirm="confirmPassword"
          @submit="handleAccept"
        />

        <Button
          label="Accept invitation"
          class="login-submit"
          :loading="accepting"
          :disabled="!canSubmit"
          @click="handleAccept"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'

import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import SetPasswordFields from '@/components/auth/SetPasswordFields.vue'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states'
import { apiUrl } from '@/util/helpers'

interface InviteProvider {
  id: string
  name: string
  provider: string | null
}

interface InviteDetails {
  email: string
  displayName: string | null
  organizationName: string | null
  applicationId: string | null
  invitedByName: string
  providers: InviteProvider[]
}

type Phase = 'loading' | 'ready' | 'appAccepted' | 'invalid'

/**
 * The unauthenticated invitation-accept page, reached from the emailed accept link
 * (`/invite/accept?token=…`). Shows who invited the visitor and into what, then lets them
 * accept with a password or any provider the target scope offers. The OIDC route returns
 * here with `?accepted=app` (application members see a confirmation since this web app is
 * not their UI) or `?error=<code>` on failure.
 */
const phase = ref<Phase>('loading')
const details = ref<InviteDetails>({ email: '', displayName: null, organizationName: null, applicationId: null, invitedByName: '', providers: [] })
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const accepting = ref(false)
const acceptedApplicationId = ref('')
const invalidMessage = ref('Open this page from the link in your invitation email, or ask to be invited again.')

const token = ref('')
const toast = useToast()
const userState = StructuresStates.getUserState()

const passwordFields = ref<InstanceType<typeof SetPasswordFields>>()

const route = useRoute()
const router = useRouter()

const canSubmit = computed<boolean>(() => {
  return !!password.value && !!confirmPassword.value && password.value === confirmPassword.value
})

onMounted(async () => {
  const query = route.query

  if (query.accepted === 'app') {
    acceptedApplicationId.value = (query.application as string) || 'the application'
    phase.value = 'appAccepted'
    return
  }

  token.value = (query.token as string) || ''
  const error = query.error as string | undefined
  if (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: errorCodeToMessage(error), life: 10000 })
    // Drop the error from the URL so a refresh doesn't replay it.
    const newQuery = { ...query }
    delete newQuery.error
    router.replace({ query: newQuery })
  }

  if (!token.value) {
    phase.value = 'invalid'
    return
  }
  await loadDetails()
})

async function loadDetails() {
  try {
    const res = await fetch(apiUrl('/api/auth/invite/details?token=' + encodeURIComponent(token.value)), { credentials: 'include' })
    if (!res.ok) {
      invalidMessage.value = await readError(res, invalidMessage.value)
      phase.value = 'invalid'
      return
    }
    const data = await res.json()
    details.value = {
      email: data.email,
      displayName: data.displayName ?? null,
      organizationName: data.organizationName ?? null,
      applicationId: data.applicationId ?? null,
      invitedByName: data.invitedByName,
      providers: Array.isArray(data.providers) ? data.providers : []
    }
    displayName.value = details.value.displayName ?? ''
    phase.value = 'ready'
  } catch {
    invalidMessage.value = 'Could not contact the server. Please try the link again.'
    phase.value = 'invalid'
  }
}

function startUrl(configId: string): string {
  // The token rides as a query param; the start route reads form attributes or params.
  return apiUrl('/api/auth/invite/oidc/start/' + encodeURIComponent(configId) + '?token=' + encodeURIComponent(token.value))
}

function focusPassword() {
  passwordFields.value?.focus()
}

async function handleAccept() {
  if (!canSubmit.value) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Enter a password and confirm it', life: 6000 })
    return
  }
  accepting.value = true
  try {
    const res = await fetch(apiUrl('/api/auth/invite/accept'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        token: token.value,
        password: password.value,
        displayName: displayName.value.trim() || null
      })
    })
    if (!res.ok) {
      toast.add({ severity: 'error', summary: 'Error', detail: await readError(res, 'Failed to accept invitation'), life: 8000 })
      return
    }

    if (res.status === 204) {
      // Organization member — the session is established; open the realtime connection.
      await userState.login()
      await CONTINUUM_UI.navigate('/applications')
      return
    }
    const data = await res.json()
    acceptedApplicationId.value = data.applicationId || 'the application'
    phase.value = 'appAccepted'
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: err instanceof Error ? err.message : 'Failed to accept invitation',
      life: 8000
    })
  } finally {
    accepting.value = false
  }
}

function errorCodeToMessage(code: string): string {
  switch (code) {
    case 'invite_invalid':     return 'This invitation is invalid, expired, or already used. Ask for a new invitation.'
    case 'email_mismatch':     return "That account's email doesn't match the invited address. Sign in with the matching account, or set a password instead."
    case 'email_not_verified': return 'Your identity provider has not verified your email address.'
    case 'state_mismatch':     return 'The sign-in attempt expired or was tampered with. Please try again.'
    case 'access_denied':      return 'You declined to authorize the sign-in. Please try again to continue.'
    default:                   return 'Sign-in failed. Please try again or set a password instead.'
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
</script>

<style scoped>
.invite-message {
  text-align: center;
  padding: 2rem 0;
}

.invite-message__icon {
  font-size: 3rem;
  color: var(--p-primary-500);
  margin-bottom: 1rem;
}

.invite-message__icon--success {
  color: var(--p-green-500);
}

.invite-providers {
  width: min(100%, 20rem);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0.5rem 0 1.5rem;
}
</style>
