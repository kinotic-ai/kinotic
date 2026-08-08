<template>
  <AuthPageShell>
    <div v-if="!token" class="login-form">
      <div class="signup-error">
        <span class="pi pi-exclamation-triangle signup-error__icon"></span>
        <h2 class="signup-title">Missing registration token</h2>
        <p class="signup-success__text">
          Open this page from the link your identity provider sent you, or
          <router-link to="/signup" class="login-link">start a new sign-up</router-link>.
        </p>
      </div>
    </div>

    <div v-else-if="step === 'connect'" class="login-form">
      <div class="connect-github">
        <span class="connect-github__icon-wrap">
          <span class="pi pi-github connect-github__icon"></span>
        </span>
        <h2 class="signup-title">Your organization is ready</h2>
        <p class="connect-github__text">
          One more step: we'll send you back to GitHub to install the Kinotic app.
          Signing in proved who you are — installing the app authorizes repository
          access, so Kinotic can create and manage the GitHub repositories that back
          your projects.
        </p>
        <Button
          label="Continue to GitHub"
          icon="pi pi-arrow-right"
          icon-pos="right"
          class="login-submit"
          :loading="connecting"
          @click="continueToGithub"
        />
      </div>
    </div>

    <div v-else class="login-form">
      <h2 class="signup-title">Name your organization</h2>
      <p class="login-form__subtitle">
        Welcome! Pick a name for your new organization to finish creating your account.
      </p>

      <div class="login-form__step">
        <InputText
          ref="orgName"
          v-model="orgName"
          class="login-input"
          placeholder="Organization name"
          @keyup.enter="focusNext('orgDescription')"
        />

        <InputText
          ref="orgDescription"
          v-model="orgDescription"
          class="login-input"
          placeholder="Description (optional)"
          @keyup.enter="handleSubmit"
        />

        <Button
          label="Create organization"
          class="login-submit"
          :loading="loading"
          @click="handleSubmit"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef } from 'vue'
import { useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

import { CONTINUUM_UI } from '@/IContinuumUI'
import { Kinotic } from '@kinotic-ai/core'
import { KinoticStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'
import { apiUrl, readAuthError } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import type { CompleteOrgRequest } from '@kinotic-ai/os-api'

/**
 * Lands here after `/api/auth/org/signup/social/callback/:configId` redirects with `?token=<verificationToken>`
 * (a {@code PendingRegistration}). The user supplies an org name; we POST to
 * `/api/auth/org/signup/social/complete`, the backend creates the Organization + admin ParticipantIdentity and
 * establishes the browser session, which we then use to open the realtime connection. A
 * "Connect GitHub" step then explains the repository-access authorization before sending the
 * tab into the GitHub App install so the new org is project-ready.
 */
const orgName = ref('')
const orgDescription = ref('')
const loading = ref(false)
const step = ref<'form' | 'connect'>('form')
const connecting = ref(false)

const toast = useToast()
const userState: IUserState = KinoticStates.getUserState()
const route = useRoute()

const orgNameInput = useTemplateRef<any>('orgName')
const orgDescriptionInput = useTemplateRef<any>('orgDescription')
const inputRefs: Record<string, { readonly value: any }> = {
  orgName: orgNameInput,
  orgDescription: orgDescriptionInput,
}

const token = computed<string | null>(() => {
  const t = route.query.token ?? new URLSearchParams(window.location.search).get('token')
  return typeof t === 'string' && t.length > 0 ? t : null
})

onMounted(() => {
  if (token.value) nextTick(() => focusNext('orgName'))
})

function focusNext(refName: string) {
  const el = inputRefs[refName]?.value as any
  el?.$el?.querySelector?.('input')?.focus?.() ?? el?.focus?.()
}

async function handleSubmit() {
  const tokenValue = token.value
  if (!tokenValue) return
  const orgNameValue = orgName.value.trim()
  if (!orgNameValue) {
    displayError('Organization name is required')
    return
  }

  loading.value = true
  try {
    const req: CompleteOrgRequest = {
      token: tokenValue,
      orgName: orgNameValue,
      orgDescription: orgDescription.value.trim() || null,
    }
    const res = await fetch(apiUrl('/api/auth/org/signup/social/complete'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(req)
    })
    if (!res.ok) {
      const message = await readAuthError(res, 'Could not create organization')
      displayError(message)
      return
    }
    // The org and admin user are created and the session established; connect with it.
    await userState.login()
    // Explain the second GitHub round-trip (repository access) before redirecting —
    // landing on GitHub again unannounced right after authorizing reads as an error.
    step.value = 'connect'
  } catch (err) {
    displayError(err instanceof Error ? err.message : 'Sign-up failed')
  } finally {
    loading.value = false
  }
}

/**
 * Sends the tab to the GitHub App install page with the applications page as the
 * return destination, so the new org can create projects immediately.
 */
async function continueToGithub(): Promise<void> {
  connecting.value = true
  try {
    // Full-tab redirect — a popup can't work here: after the round-trip through
    // github.com, COOP severs window.opener (see NewProjectSidebar.linkGitHub).
    // startInstall requires the org-scoped session userState.login() just opened.
    const url = await Kinotic.githubAppInstallations.startInstall('/applications')
    window.location.href = url
  } catch {
    // Install couldn't start (e.g. a kinotic.disableGithub deployment) — signup must
    // never dead-end here; the user can link GitHub later from Organization Settings.
    await CONTINUUM_UI.navigate('/applications')
  }
}

function displayError(text: string) {
  toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
}
</script>

<style scoped>
.signup-error {
  text-align: center;
  padding: 2rem 0;
}

.signup-error__icon {
  font-size: 3rem;
  color: var(--p-primary-500);
  margin-bottom: 1rem;
}

.connect-github {
  text-align: center;
  padding: 1rem 0 0.5rem;
}

.connect-github__icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
  margin-bottom: 1.5rem;
}

.connect-github__icon {
  font-size: 2rem;
  color: var(--p-primary-color);
}

.connect-github__text {
  margin: 0 auto 1.5rem;
  max-width: 24rem;
  line-height: 1.5;
}
</style>
