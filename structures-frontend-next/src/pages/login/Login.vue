<template>
  <div class="flex w-full justify-center items-center h-screen mx-auto">
    <div
      v-if="isInitialized && state?.oidcCallbackLoading"
      class="fixed inset-0 bg-white bg-opacity-90 flex items-center justify-center z-50"
    >
      <div class="text-center">
        <div
          class="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-600 mx-auto mb-4"
        ></div>
        <h2 class="text-xl font-semibold text-gray-800 mb-2">
          Validating Login...
        </h2>
        <p class="text-gray-600">
          Please wait while we complete your authentication.
        </p>
      </div>
    </div>

    <div
      class="relative w-1/2 h-full bg-gradient-to-br from-[#0A0A0B] from-0% via-[#0A0A0B] via-70% to-[#293A9E] to-100% hidden md:block"
    >
      <img
        src="@/assets/login-page-symbol-new.svg"
        class="absolute right-0 bottom-0 h-screen"
      />
      <img
        src="@/assets/login-page-logo-new.svg"
        class="absolute left-[75px] bottom-[56px] max-w-[300px] h-[63px] w-auto xl:max-w-[300px] xl:h-[63px] lg:max-w-[250px] lg:h-[52px] md:max-w-[200px] md:h-[42px] sm:max-w-[150px] sm:h-[32px]"
      />
    </div>

    <div class="w-1/2 h-full flex flex-col justify-center items-center bg-center bg-cover relative">
      <div class="w-[320px] flex flex-col items-center">
        <img
          src="@/assets/login-page-logo.svg"
          class="w-[218px] h-[45px] mb-[53px]"
        />

        <div v-if="isInitialized && shouldShowLoginForm">
          <div
            v-if="state?.showRetryOption"
            class="w-full mb-6 p-4 border border-red-200 bg-red-50 rounded-lg"
          >
            <div class="text-center">
              <div class="flex items-center justify-center mb-3">
                <svg
                  class="w-5 h-5 text-red-600 mr-2"
                  fill="currentColor"
                  viewBox="0 0 20 20"
                >
                  <path
                    fill-rule="evenodd"
                    d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
                    clip-rule="evenodd"
                  ></path>
                </svg>
                <p class="text-red-700 font-medium">
                  OIDC login encountered an error
                </p>
              </div>
              <p class="text-red-600 mb-4 text-sm">
                You can try again or use an alternative login method:
              </p>

              <div class="mb-4">
                <button
                  @click="toggleErrorDetails"
                  class="text-red-600 hover:text-red-800 text-sm underline flex items-center mx-auto"
                >
                  <span>
                    {{ state?.showErrorDetails ? 'Hide' : 'Show' }} error
                    details
                  </span>
                  <svg
                    class="w-4 h-4 ml-1 transition-transform"
                    :class="{ 'rotate-180': state?.showErrorDetails }"
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path
                      fill-rule="evenodd"
                      d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                      clip-rule="evenodd"
                    ></path>
                  </svg>
                </button>

                <div
                  v-if="state?.showErrorDetails && currentOidcError"
                  class="mt-3 p-3 bg-red-100 border border-red-200 rounded text-left"
                >
                  <div class="text-xs text-red-700 font-mono">
                    <div><strong>Error:</strong> {{ currentOidcError.error }}</div>
                    <div
                      v-if="currentOidcError.description"
                      class="mt-1"
                    >
                      <strong>Description:</strong>
                      {{ currentOidcError.description }}
                    </div>
                  </div>
                </div>
              </div>

              <div class="flex flex-wrap gap-2 justify-center">
                <Button
                  label="Try OIDC Again"
                  class="rounded-[10px] max-h-[40px] !py-2 !px-4 !text-sm bg-blue-600 hover:bg-blue-700"
                  @click="retryOidcLogin"
                />
                <Button
                  label="Use Password Instead"
                  class="rounded-[10px] max-h-[40px] !py-2 !px-4 !text-sm bg-gray-600 hover:bg-gray-700"
                  @click="usePasswordInstead"
                />
                <Button
                  label="Back to Email"
                  class="rounded-[10px] max-h-[40px] !py-2 !px-4 !text-sm bg-gray-500 hover:bg-gray-600"
                  @click="goBackToEmail"
                />
                <Button
                  label="Clear Error"
                  class="rounded-[10px] max-h-[40px] !py-2 !px-4 !text-sm bg-red-600 hover:bg-blue-700"
                  @click="clearErrorAndReset"
                />
              </div>
            </div>
          </div>

          <div v-if="!state?.emailEntered" class="w-full">
            <IconField class="!mb-6 !flex !items-center !relative !w-full">
              <InputText
                ref="emailInput"
                v-model="login"
                class="w-[320px] max-w-[700px] h-[56px] !pl-4"
                placeholder="Username or Email"
                @focus="hideAlert"
                @keydown.enter.prevent="handleEmailSubmit"
              />
            </IconField>

            <Button
              label="Continue"
              type="button"
              class="rounded-[10px] max-h-[56px] !py-[18px] !w-full !text-base"
              :loading="state?.loading || false"
              @click="goToPasswordOnly"
            />
          </div>

          <!-- Password Step -->
          <div v-if="state?.emailEntered" class="w-full">
            <div
              v-if="state?.matchedProvider && !state?.showPassword"
              class="w-full"
            >
              <h2
                class="text-xl font-semibold text-gray-800 mb-6 text-center"
              >
                Continue with
                {{ state?.providerDisplayName || state?.matchedProvider }}
              </h2>

              <div
                class="flex border border-[#B8BCBD] w-full p-4 mb-6 rounded-[5px] text-black/50 cursor-pointer hover:bg-gray-50 transition-colors"
                :class="{ 'opacity-50 cursor-not-allowed': state?.loading }"
                @click="handleOidcLogin(state?.matchedProvider)"
              >
                <img
                  :src="getProviderIcon(state?.matchedProvider)"
                  class="mr-6 w-6 h-6"
                />
                <span v-if="!state?.loading">
                  Continue with
                  {{ state?.providerDisplayName || state?.matchedProvider }}
                </span>
                <span v-else class="flex items-center">
                  <div
                    class="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-600 mr-2"
                  ></div>
                  Connecting...
                </span>
              </div>

              <div class="text-center">
                <button
                  @click="showPassword = true"
                  class="text-[#0568FD] hover:underline cursor-pointer"
                >
                  Or sign in with password
                </button>
              </div>
            </div>

            <div v-if="state?.showPassword" class="w-full">
              <IconField class="!mb-6 !flex !items-center !relative !w-full">
                <InputText
                  :value="login"
                  disabled
                  class="w-[320px] max-w-[700px] h-[56px] !pl-4 !bg-gray-100 !text-gray-600"
                  placeholder="Username or Email"
                />
              </IconField>

              <IconField class="!mb-6 !flex !items-center !relative !w-full">
                <Password
                  ref="passwordInput"
                  v-model="password"
                  input-class="w-full h-[56px]"
                  class="!w-full max-w-[700px]"
                  placeholder="Password"
                  toggleMask
                  :feedback="false"
                  @focus="hideAlert"
                  @keyup.enter="handleLogin"
                />
              </IconField>
              <Button
                label="Sign In"
                class="rounded-[10px] max-h-[56px] !py-[18px] !w-full !text-base !mb-5"
                :loading="state?.loading || false"
                @click="handleLogin"
              />
              <Button
                label="< Back"
                text
                class="!p-3 !w-full !text-base"
                style="color: #3651ED; background: transparent;"
                @click="resetToEmail"
              />
            </div>

            <div
              v-if="!state?.matchedProvider && !state?.showPassword && state?.emailEntered"
              class="w-full text-center"
            >
              <div class="text-red-600 mb-4">
                No authentication method found for this email domain.
              </div>
              <button
                @click="resetToEmail"
                class="text-[#0568FD] hover:underline cursor-pointer"
              >
                Try a different email
              </button>
            </div>
          </div>
        </div>

        <div v-if="!isInitialized" class="w-full text-center">
          <div
            class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4"
          ></div>
          <p class="text-gray-600">Initializing...</p>
        </div>
      </div>

      <div
        class="flex gap-2 absolute bottom-8 left-0 right-0 justify-center"
      >
        <a href="#" class="text-[#0568FD] border-b-1"> Terms of use </a>
        <span class="text-gray-400"> | </span>
        <span class="text-[#0568FD] border-b-1"> Privacy policy </span>
      </div>
    </div>

    <Toast />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { createUserManager } from './OidcConfiguration'
import { AuthenticationService } from '@/util/AuthenticationService'
import { type IUserState } from '@/states/IUserState'
import { CONTINUUM_UI } from '@/IContinuumUI'
import { StructuresStates } from '@/states/index'

@Component({
  components: {
    InputText,
    Password,
    Button,
    Toast,
  },
})
export default class Login extends Vue {
  private auth = new AuthenticationService()
  toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  get login() { return this.auth.login }
  set login(value: string) { this.auth.login = value }

  get password() { return this.auth.password }
  set password(value: string) { this.auth.password = value }

  get state() { return this.auth.state }
  get shouldShowLoginForm() { return this.auth.shouldShowLoginForm }
  get isLoginValid() { return this.auth.isLoginValid }
  get isPasswordValid() { return this.auth.isPasswordValid }
  get currentOidcError() { return this.auth.currentOidcError }

  get showPassword() { return this.state.showPassword }
  set showPassword(value: boolean) { this.auth.updateState({ showPassword: value }) }

  get isInitialized() { return true }

  private _isConfigLoaded = false
  private _isBasicAuthEnabled = true

  get isConfigLoaded() { return this._isConfigLoaded }
  get isBasicAuthEnabled() { return this._isBasicAuthEnabled }

  async mounted() {
    this.$nextTick(() => this.focusEmailInput())
    this.loadBasicConfig()

    if (this.$route.query.error) {
      this.handleOidcError()
      return
    }

    if (this.$route.query.code && this.$route.query.state) {
      this.handleOidcCallback()
    }
  }

  private async loadBasicConfig() {
    try {
      this._isBasicAuthEnabled = await this.auth.checkBasicAuthEnabled()
      this._isConfigLoaded = true
    } catch (error) {
      this._isBasicAuthEnabled = true
      this._isConfigLoaded = false
    }
  }

  private focusEmailInput() {
    if (this.$refs.emailInput) {
      (this.$refs.emailInput as any).$el?.focus?.() ||
        (this.$refs.emailInput as any).focus?.()
    }
  }

  private focusPasswordInput() {
    if (this.$refs.passwordInput) {
      const passwordElement = this.$refs.passwordInput as any
      const innerInput =
        passwordElement.$el?.querySelector('input[type="password"]') ||
        passwordElement.$el?.querySelector('input')
      if (innerInput) innerInput.focus()
    }
  }

  private hideAlert() {
    this.toast.removeAllGroups()
  }

  private displayAlert(text: string) {
    this.toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 3000,
    })
  }

  private async handleEmailSubmit() {
    if (!this.isLoginValid) {
      this.displayAlert('Please enter a valid email address')
      return
    }

    this.auth.setLoading(true)
    try {
      this.auth.updateState({
        emailEntered: true,
        showPassword: true,
        matchedProvider: null,
        providerDisplayName: '',
        showRetryOption: false,
        showErrorDetails: false,
      })
      this.$nextTick(() => this.focusPasswordInput())
    } finally {
      this.auth.setLoading(false)
    }
  }

  private goToPasswordOnly() {
    this.auth.updateState({
      emailEntered: true,
      showPassword: true,
      matchedProvider: null,
      providerDisplayName: '',
      showRetryOption: false,
      showErrorDetails: false,
    })
    this.$nextTick(() => this.focusPasswordInput())
  }

  async handleLogin() {

    if (!this.state?.showPassword) return

    if (!this.password || this.password.trim() === '') {
      this.$nextTick(() => this.focusPasswordInput())
      return
    }

    if (!this.isLoginValid) {
      this.displayAlert('Please enter a valid email address')
      return
    }

    if (!this.isPasswordValid) {
      this.displayAlert('Please enter your password')
      return
    }

    this.auth.setLoading(true)
    try {
      await this.userState.authenticate(this.login, this.password)
      const redirect =
        this.referer || this.$route.redirectedFrom?.fullPath || '/applications'
      await CONTINUUM_UI.navigate(redirect)
    } catch (error: any) {
      this.displayAlert(
        error?.message || typeof error === 'string'
          ? error
          : 'Unknown login error'
      )
      this.auth.resetToEmail()
    } finally {
      this.auth.setLoading(false)
    }
  }

  get referer(): string | null {
    const r = this.$route.query.referer
    return typeof r === 'string' ? r : null
  }
}
</script>
