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

      <!-- an application's providers return to the application, whose login claims the invited user on the first sign-in -->
      <div v-if="details.applicationId && details.providers.length > 0" class="w-full max-w-80 flex flex-col gap-3 mt-2 mb-6">
        <p class="login-form__subtitle">
          Sign in to <strong>{{ details.applicationId }}</strong> with {{ providerNames }} as <strong>{{ details.email }}</strong>;
          your first sign-in accepts this invitation.
        </p>
        <Button v-if="details.signInUrl" as="a" :href="details.signInUrl" :label="`Continue to ${details.applicationId}`" class="login-submit" />
        <div v-if="details.localEnabled" class="login-divider"><span>or set a password</span></div>
      </div>
      <div v-else-if="details.providers.length > 0" class="w-full max-w-80 flex flex-col gap-3 mt-2 mb-6">
        <SocialAuthButton
          v-for="provider in details.providers"
          :key="provider.id"
          :provider="provider.provider || provider.name"
          :action="startUrl(provider.id)"
          intent="sign-up"
        />
        <div v-if="details.localEnabled" class="login-divider"><span>or set a password</span></div>
      </div>

      <!-- an application with no provider and no password left takes the invitation once one is turned back on -->
      <p v-if="!details.localEnabled && details.applicationId && details.providers.length === 0" class="login-form__subtitle">
        <strong>{{ details.applicationId }}</strong> offers no way to sign in right now, so this invitation cannot be accepted.
        Ask {{ details.invitedByName }} to turn on a sign-in method for it, then open this link again.
      </p>
      <p v-else-if="!details.localEnabled && !details.applicationId" class="login-form__subtitle">
        {{ details.organizationName }} accepts invitations through its sign-in providers. Continue with one of them as
        <strong>{{ details.email }}</strong> to join.
      </p>
      <div v-else-if="details.localEnabled" class="login-form__step">
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
  /** Whether the invitation may be accepted with a password, which a scope allows only with password sign-in on. */
  localEnabled: boolean
  providers: InviteProvider[]
  /** An application's primary UI, where signing in through one of its providers accepts its invitation; null otherwise. */
  signInUrl: string | null
}

type Phase = 'loading' | 'ready' | 'appAccepted' | 'invalid'

/**
 * The unauthenticated invitation-accept page, reached from the emailed accept link
 * (`/invite/accept?token=…`). Shows who invited the visitor and into what, then lets them
 * accept with a password, into a scope that allows password sign-in, or any provider the organization's login
 * offers; an application invitee sets a password and is sent to the application's primary UI,
 * or sees a confirmation while the application has none, or signs in to the application through one of its
 * providers, whose first sign-in accepts the invitation. The OIDC route returns here with
 * `?error=<code>` on failure.
 */
const phase = ref<Phase>('loading')
const details = ref<InviteDetails>({ email: '', displayName: null, organizationName: null, applicationId: null, invitedByName: '', localEnabled: false, providers: [], signInUrl: null })
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
// the application's providers by name, as its login page shows them: "Google, Okta or GitHub"
const providerNames = computed<string>(() => {
  const names = details.value.providers.map(provider => provider.name)
  return names.length > 1 ? `${names.slice(0, -1).join(', ')} or ${names[names.length - 1]}` : names.join('')
})

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
      providers: Array.isArray(data.providers) ? data.providers : [],
      signInUrl: data.signInUrl ?? null
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

