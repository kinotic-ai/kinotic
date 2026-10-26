<template>
  <AuthPageShell>
    <div v-if="phase === 'loading'" class="login-form">
      <div class="login-loading-state">
        <div class="login-spinner login-spinner--small"></div>
      </div>
    </div>

    <div v-else-if="phase === 'invalid'" class="login-form">
      <div class="text-center py-8">
        <span class="login-status-icon pi pi-exclamation-triangle"></span>
        <h2 class="signup-title">Invitation unavailable</h2>
        <p class="login-form__subtitle">{{ invalidMessage }}</p>
      </div>
    </div>

    <div v-else-if="phase === 'appAccepted'" class="login-form">
      <div class="text-center py-8">
        <span class="pi pi-check-circle text-5xl text-[var(--p-green-500)] mb-4"></span>
        <h2 class="signup-title">You're all set</h2>
        <p class="login-form__subtitle">
          You are now a user of <strong>{{ acceptedApplicationId }}</strong>.
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

      <div v-if="details.providers.length > 0" class="w-full max-w-80 flex flex-col gap-3 mt-2 mb-6">
        <SocialAuthButton
          v-for="provider in details.providers"
          :key="provider.id"
          :provider="provider.provider || provider.name"
          :action="startUrl(provider.id)"
          intent="sign-up"
        />
        <div v-if="details.localEnabled" class="login-divider"><span>or set a password</span></div>
      </div>

      <!-- an application's invitation is accepted with a password alone, so one whose login stopped taking it waits for that -->
      <p v-if="!details.localEnabled && details.applicationId" class="login-form__subtitle">
        <strong>{{ details.applicationId }}</strong> does not take passwords right now, so this invitation cannot be accepted.
        Ask {{ details.invitedByName }} to turn on email and password sign-in for it, then open this link again.
      </p>
      <p v-else-if="!details.localEnabled" class="login-form__subtitle">
        {{ details.organizationName }} accepts invitations through its sign-in providers. Continue with one of them as
        <strong>{{ details.email }}</strong> to join.
      </p>
      <div v-else class="login-form__step">
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

import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SetPasswordFields from '@/components/SetPasswordFields.vue'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
import { KINOTIC_UI } from '@/IKinoticUI'
import { emailNotVerifiedMessage } from '@/util/oidcConfigurations'
import { KinoticStates } from '@/states'
import { apiUrl, readAuthError } from '@kinotic-ai/frontend-common'

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
  /** Whether the invitation may be accepted with a password, which an organization allows only with password sign-in on. */
  localEnabled: boolean
  providers: InviteProvider[]
}

type Phase = 'loading' | 'ready' | 'appAccepted' | 'invalid'

/**
 * The unauthenticated invitation-accept page, reached from the emailed accept link
 * (`/invite/accept?token=…`). Shows who invited the visitor and into what, then lets them
 * accept with a password, into an organization that allows password sign-in, or any provider the organization's login
 * offers; an application invitee sets a password and is sent to the application's primary UI,
 * or sees a confirmation while the application has none. The OIDC route returns here with
 * `?error=<code>` on failure.
 */
const phase = ref<Phase>('loading')
const details = ref<InviteDetails>({ email: '', displayName: null, organizationName: null, applicationId: null, invitedByName: '', localEnabled: false, providers: [] })
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const accepting = ref(false)
const acceptedApplicationId = ref('')
const invalidMessage = ref('Open this page from the link in your invitation email, or ask to be invited again.')

const token = ref('')
const toast = useToast()
const userState = KinoticStates.getUserState()

const passwordFields = ref<InstanceType<typeof SetPasswordFields>>()

const route = useRoute()
const router = useRouter()

const canSubmit = computed<boolean>(() => !!password.value && !!confirmPassword.value && password.value === confirmPassword.value)

onMounted(async () => {
  const query = route.query

  token.value = (query.token as string) || ''
  const error = query.error as string | undefined
  if (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: errorCodeToMessage(error, query.provider as string | undefined), life: 10000 })
    // Drop the error from the URL so a refresh doesn't replay it.
    const newQuery = { ...query }
    delete newQuery.error
    delete newQuery.provider
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
      invalidMessage.value = await readAuthError(res, invalidMessage.value)
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
      localEnabled: data.localEnabled === true,
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
      toast.add({ severity: 'error', summary: 'Error', detail: await readAuthError(res, 'Failed to accept invitation'), life: 8000 })
      return
    }

    if (res.status === 204) {
      // Organization member — the session is established; open the realtime connection.
      await userState.login()
      await KINOTIC_UI.navigate('/')
      return
    }
    const data = await res.json()
    if (data.primaryUiUrl) {
      // replace, so Back does not return to the consumed invitation
      window.location.replace(data.primaryUiUrl)
      return
    }
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

function errorCodeToMessage(code: string, provider: string | undefined): string {
  switch (code) {
    case 'invite_invalid':     return 'This invitation is invalid, expired, or already used. Ask for a new invitation.'
    case 'email_mismatch':     return "That account's email doesn't match the invited address. Sign in with the matching account, or use your password instead."
    case 'user_exists':        return 'This email is already a member of the organization. Sign in instead.'
    case 'user_disabled':      return 'You cannot sign in to this organization. Ask an administrator to enable your access.'
    case 'email_not_verified': return emailNotVerifiedMessage(provider)
    case 'state_mismatch':     return 'The sign-in attempt expired or was tampered with. Please try again.'
    case 'access_denied':      return 'You declined to authorize the sign-in. Please try again to continue.'
    default:                   return 'Sign-in failed. Please try again or set a password instead.'
  }
}

</script>

