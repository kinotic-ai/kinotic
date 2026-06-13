<template>
  <AuthPageShell :art="loginBackgroundArt" :show-theme-toggle="false">
    <div class="login-form">
      <div v-if="!userCode" class="device-message">
        <span class="pi pi-exclamation-triangle device-message__icon"></span>
        <h2 class="signup-title">Missing device code</h2>
        <p class="login-form__subtitle">
          Open the verification link shown in your command line.
        </p>
      </div>

      <div v-else-if="approved" class="device-message">
        <span class="pi pi-check-circle device-message__icon"></span>
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
        <div class="device-code">{{ userCode }}</div>
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

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator'
import { Kinotic } from '@kinotic-ai/core'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

import loginPageLeft from '@/assets/login-page-left.svg'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'

/**
 * The RFC 8628 device-verification page. The CLI sends the user here
 * (`/device?user_code=<code>`); the signed-in user confirms the code, which binds their
 * account to the pending CLI authorization grant.
 */
@Component({
  components: { AuthPageShell, Button }
})
export default class DeviceVerification extends Vue {
  loading = false
  approved = false

  private readonly loginBackgroundArt = loginPageLeft
  private toast = useToast()

  get userCode(): string | null {
    const code = this.$route.query.user_code
    return typeof code === 'string' && code.length > 0 ? code : null
  }

  async handleApprove() {
    const userCode = this.userCode
    if (!userCode) return
    this.loading = true
    try {
      await Kinotic.deviceApproval.approve(userCode)
      this.approved = true
    } catch (err) {
      this.displayError(err instanceof Error ? err.message : 'Could not approve the device')
    } finally {
      this.loading = false
    }
  }

  private displayError(text: string) {
    this.toast.add({ severity: 'error', summary: 'Error', detail: text, life: 10000 })
  }
}
</script>

<style scoped>
.device-message {
  text-align: center;
  padding: 2rem 0;
}

.device-message__icon {
  font-size: 3rem;
  color: var(--p-primary-500);
  margin-bottom: 1rem;
}

.device-code {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: 0.3em;
  text-align: center;
  margin: 1.5rem 0;
}
</style>
