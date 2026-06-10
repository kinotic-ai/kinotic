<template>
  <AuthPageShell>
    <div class="login-form">
      <!-- Password form -->
      <div class="login-form__step">
        <div class="verify-header">
          <span class="verify-icon-wrap verify-icon-wrap--primary">
            <span class="pi pi-shield verify-header__icon"></span>
          </span>
          <h2 class="verify-title">Email verified</h2>
          <p class="verify-text">Name your organization and set a password to finish.</p>
        </div>

        <div class="login-field">
          <InputText
            ref="orgNameInput"
            v-model="request.orgName"
            class="login-input"
            placeholder="Organization name"
            @keyup.enter="focusPassword"
          />
        </div>

        <SetPasswordFields
          ref="passwordFields"
          v-model:password="request.password"
          v-model:confirm="confirmPassword"
          @submit="handleSubmit"
        />

        <Button
          label="Create account"
          class="login-submit"
          :loading="loading"
          :disabled="!canSubmit"
          @click="handleSubmit"
        />
      </div>
    </div>
  </AuthPageShell>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator';
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'
import type { SignUpCompleteRequest } from '@kinotic-ai/os-api'

import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import SetPasswordFields from '@/components/auth/SetPasswordFields.vue'
import { apiUrl } from '@/util/helpers'
import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states/index'
import { type IUserState } from '@/states/IUserState'

@Component({
  components: {
    AuthPageShell,
    SetPasswordFields,
    Button,
    InputText,
  }
})
export default class VerifyEmail extends Vue {
  private toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  get canSubmit(): boolean {
    return !!this.request.orgName
        && !!this.request.password
        && !!this.confirmPassword
        && this.request.password === this.confirmPassword
  }

  request: SignUpCompleteRequest = {
    token: '',
    orgName: '',
    password: '',
  }
  confirmPassword = ''
  loading = false

  mounted() {
    this.request.token = (this.$route.query.token as string) || ''
    if (!this.request.token) {
      this.displayAlert('No verification token provided.')
    }
  }

  private focusPassword() {
    const fields = this.$refs.passwordFields as InstanceType<typeof SetPasswordFields> | undefined
    fields?.focus()
  }

  async handleSubmit() {
    if (!this.request.token) {
      this.displayAlert('No verification token provided.')
      return
    }
    this.request.orgName = this.request.orgName.trim()
    if (!this.request.orgName) {
      this.displayAlert('Organization name is required')
      return
    }
    if (!this.request.password) {
      this.displayAlert('Password is required')
      return
    }
    if (this.request.password !== this.confirmPassword) {
      this.displayAlert('Passwords do not match')
      return
    }

    this.loading = true
    try {
      const response = await fetch(apiUrl('/api/signup/complete'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(this.request),
      })

      if (!response.ok) {
        this.displayAlert(await this.readError(response, 'Account creation failed'))
        return
      }

      // The org, admin user, and browser session are created; connect with it and go to the app.
      await this.userState.login()
      await CONTINUUM_UI.navigate('/applications')
    } catch (error: unknown) {
      this.displayAlert(error instanceof Error ? error.message : 'Account creation failed')
    } finally {
      this.loading = false
    }
  }

  private async readError(res: Response, fallback: string): Promise<string> {
    try {
      const body = await res.json()
      return body?.error ?? fallback
    } catch {
      return fallback
    }
  }

  private displayAlert(text: string) {
    this.toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 10000
    })
  }
}
</script>

<style scoped>
.verify-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 999px;
  margin-bottom: 1.5rem;
}

.verify-icon-wrap--primary {
  background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
}

.verify-header__icon {
  font-size: 2rem;
}

.verify-icon-wrap--primary .verify-header__icon {
  color: var(--p-primary-color);
}

.verify-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.5rem;
  text-align: center;
  line-height: 1.25;
}

.verify-text {
  margin: 0.25rem 0 1.5rem;
  line-height: 1.5;
  color: var(--p-text-muted-color);
  text-align: center;
}
</style>
