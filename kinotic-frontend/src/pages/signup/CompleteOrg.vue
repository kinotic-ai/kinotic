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

    <div v-else class="login-form">
      <h2 class="signup-title">Name your organization</h2>
      <p class="login-form__subtitle">
        Welcome! Pick a name for your new organization to finish creating your account.
        Next, you'll install the Kinotic GitHub App — every project is backed by a
        GitHub repository.
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
import { apiUrl } from '@/util/helpers'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import type { CompleteOrgRequest } from '@kinotic-ai/os-api'

/**
 * Lands here after `/api/auth/org/signup/social/callback/:configId` redirects with `?token=<verificationToken>`
 * (a {@code PendingRegistration}). The user supplies an org name; we POST to
 * `/api/auth/org/signup/social/complete`, the backend creates the Organization + admin IamUser and
 * establishes the browser session, which we then use to open the realtime connection and send
 * the tab straight into the GitHub App install so the new org is project-ready.
 */
const orgName = ref('')
const orgDescription = ref('')
const loading = ref(false)

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
      const message = await readError(res, 'Could not create organization')
      displayError(message)
      return
    }
    // The org and admin user are created and the session established; connect with it.
    await userState.login()
    await startGithubInstall()
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
async function startGithubInstall(): Promise<void> {
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

async function readError(res: Response, fallback: string): Promise<string> {
  try {
    const body = await res.json()
    return body?.error ?? fallback
  } catch {
    return fallback
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
</style>
