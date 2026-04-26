<template>
  <div class="login-page">
    <div v-if="isInitialized && state?.oidcCallbackLoading" class="login-overlay">
      <div class="login-overlay__content">
        <div class="login-spinner"></div>
        <h2 class="login-overlay__title">Validating Login...</h2>
        <p class="login-overlay__text">Please wait while we complete your authentication.</p>
      </div>
    </div>

    <div class="login-shell">
      <aside class="login-art" aria-hidden="true">
        <img :src="loginBackgroundArt" alt="" class="login-art__image" />
      </aside>

      <main class="login-panel">
        <button type="button" class="login-theme-toggle" @click="toggleTheme" :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'">
          <span :class="isDark ? 'pi pi-sun' : 'pi pi-moon'"></span>
        </button>
        <div class="login-panel__content">
          <img :src="loginBrandMark" alt="Kinotic" class="login-brand" />

          <div v-if="isInitialized && shouldShowLoginForm" class="login-form">
            <div v-if="state?.showRetryOption" class="login-alert">
              <div class="login-alert__header">
                <svg class="login-alert__icon" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"></path>
                </svg>
                <p class="login-alert__title">OIDC login encountered an error</p>
              </div>
              <p class="login-alert__copy">You can try again or use an alternative login method.</p>

              <button @click="toggleErrorDetails" class="login-link login-link--alert">
                {{ state?.showErrorDetails ? 'Hide' : 'Show' }} error details
              </button>

              <div v-if="state?.showErrorDetails && currentOidcError" class="login-alert__details">
                <div><strong>Error:</strong> {{ currentOidcError.error }}</div>
                <div v-if="currentOidcError.description"><strong>Description:</strong> {{ currentOidcError.description }}</div>
              </div>

              <div class="login-alert__actions">
                <Button label="Try OIDC Again" class="login-alert-button login-alert-button--primary" @click="retryOidcLogin" />
                <Button label="Use Password Instead" class="login-alert-button login-alert-button--neutral" @click="usePasswordInstead" />
                <Button label="Back to Email" class="login-alert-button login-alert-button--neutral" @click="goBackToEmail" />
                <Button label="Clear Error" class="login-alert-button login-alert-button--danger" @click="clearErrorAndReset" />
              </div>
            </div>

            <div v-if="!state?.emailEntered" class="login-form__step">
              <IconField class="login-field">
                <InputText
                  ref="emailInput"
                  v-model="login"
                  class="login-input"
                  placeholder="Username or Email"
                  @focus="hideAlert"
                  @keyup.enter="handleEmailSubmit"
                />
              </IconField>

              <Button
                label="Continue"
                class="login-submit"
                :loading="state?.loading || false"
                @click="handleEmailSubmit"
              />
            </div>

            <div v-if="state?.emailEntered" class="login-form__step">
              <div v-if="state?.matchedProvider && !state?.showPassword" class="login-provider">
                <h2 class="login-provider__title">Continue with {{ state?.providerDisplayName || state?.matchedProvider }}</h2>

                <button
                  class="login-provider__button"
                  :class="{ 'login-provider__button--disabled': state?.loading }"
                  @click="handleOidcLogin(state?.matchedProvider)"
                >
                  <img :src="getProviderIcon(state?.matchedProvider)" class="login-provider__icon" alt="" />
                  <span v-if="!state?.loading">Continue with {{ state?.providerDisplayName || state?.matchedProvider }}</span>
                  <span v-else class="login-provider__loading">
                    <span class="login-provider__spinner"></span>
                    Connecting...
                  </span>
                </button>

                <button @click="showPassword = true" class="login-link">
                  Or sign in with password
                </button>
              </div>

              <div v-if="state?.showPassword" class="login-password-step">
                <IconField class="login-field">
                  <InputText
                    :value="login"
                    disabled
                    class="login-input login-input--disabled"
                    placeholder="Username or Email"
                  />
                </IconField>

                <IconField class="login-field">
                  <Password
                    ref="passwordInput"
                    v-model="password"
                    input-class="login-password-input"
                    class="login-password"
                    placeholder="Password"
                    toggleMask
                    :feedback="false"
                    @focus="hideAlert"
                    @keyup.enter="handleLogin"
                  />
                </IconField>

                <Button
                  label="Sign in"
                  class="login-submit"
                  :loading="state?.loading || false"
                  @click="handleLogin"
                />

                <button type="button" class="login-back-link" @click="resetToEmail">
                  <span class="pi pi-angle-left login-back-link__icon" aria-hidden="true"></span>
                  <span>Back</span>
                </button>
              </div>

              <div v-if="!state?.matchedProvider && !state?.showPassword && state?.emailEntered" class="login-form__empty">
                <div class="login-form__empty-message">No authentication method found for this email domain.</div>
                <button @click="resetToEmail" class="login-link">Try a different email</button>
              </div>
            </div>
          </div>

          <div v-if="!isInitialized" class="login-loading-state">
            <div class="login-spinner login-spinner--small"></div>
            <p class="login-loading-state__text">Initializing...</p>
          </div>
        </div>

        <footer class="login-footer">
          <a href="#" class="login-footer__link">Terms of use</a>
          <span class="login-footer__divider">|</span>
          <a href="#" class="login-footer__link">Privacy policy</a>
        </footer>
      </main>
    </div>

    <Toast />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-facing-decorator';
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { createUserManager } from './OidcConfiguration';
import { AuthenticationService } from '@/util/AuthenticationService';

import { type IUserState } from "@/states/IUserState"
import { CONTINUUM_UI } from "@/IContinuumUI"
import { StructuresStates } from "@/states/index"
import { createDebug } from '@/util/debug';
import loginPageLeft from '@/assets/login-page-left.svg'
import loginPageLogo from '@/assets/login-page-kinotic-logo.svg'
import loginPageLogoLight from '@/assets/login-page-kinotic-logo-light.svg'
import { isDark as darkMode, toggleDark } from '@/composables/useTheme'

const debug = createDebug('login');

@Component({
  components: {
    InputText,
    Password,
    Button,
    Toast,
  }
})
export default class Login extends Vue {
  private auth = new AuthenticationService();
  private readonly loginBackgroundArt = loginPageLeft
  get loginBrandMark() { return darkMode.value ? loginPageLogo : loginPageLogoLight }

  get isDark() { return darkMode.value }
  toggleTheme() { toggleDark() }

  get login() { return this.auth.login; }
  set login(value: string) { this.auth.login = value; }
  
  get password() { return this.auth.password; }
  set password(value: string) { this.auth.password = value; }

  get state() { return this.auth.state; }
  get shouldShowLoginForm() { return this.auth.shouldShowLoginForm; }
  get isLoginValid() { return this.auth.isLoginValid; }
  get isPasswordValid() { return this.auth.isPasswordValid; }
  get currentOidcError() { return this.auth.currentOidcError; }

  get showPassword() { return this.state.showPassword; }
  set showPassword(value: boolean) { 
    this.auth.updateState({ showPassword: value }); 
  }

  get isInitialized() { 
    return true;
  }

  private _isConfigLoaded: boolean = false;
  private _isBasicAuthEnabled: boolean = true;

  get isConfigLoaded() { return this._isConfigLoaded; }
  get isBasicAuthEnabled() { return this._isBasicAuthEnabled; }

  private async isDebugMode(): Promise<boolean> {
    try {
      return await this.auth.isDebugEnabled();
    } catch (error) {
      return false;
    }
  }

  toast = useToast()
  private userState: IUserState = StructuresStates.getUserState()

  async mounted() {
    this.$nextTick(() => {
      this.focusEmailInput();
    });
    
    await this.loadConfig();
    
    // Check for OIDC params in window.location.search (not route query)
    // because hash-mode routing doesn't see params before the #
    const oidcParams = this.getOidcCallbackParams();
    
    if (oidcParams.error) {
      await this.handleOidcError();
      return;
    }

    if (await this.isDebugMode()) {
      console.log(`🚀 [MOUNTED] oidcParams: ${JSON.stringify(oidcParams)}`);
    }
    
    if (oidcParams.code && oidcParams.state) {
      if (await this.isDebugMode()) {
        debug('OIDC callback detected - code: %s, state: %s', oidcParams.code, oidcParams.state);
      }
      await this.handleOidcCallback();
    } else {
      if (await this.isDebugMode()) {
        debug('No OIDC callback - code: %s, state: %s', !!oidcParams.code, !!oidcParams.state);
      }
    }
  }

  private async loadConfig() {
    try {
      this._isBasicAuthEnabled = await this.auth.checkBasicAuthEnabled();
      this._isConfigLoaded = true;
    } catch (error) {
      debug('Failed to load basic config: %O', error);
      this._isBasicAuthEnabled = true;
      this._isConfigLoaded = false;
    }
  }

  private focusEmailInput() {
    if (this.$refs.emailInput) {
      (this.$refs.emailInput as any).$el?.focus?.() || 
      (this.$refs.emailInput as any).focus?.();
    }
  }

  private focusPasswordInput() {
    if (this.$refs.passwordInput) {
      const passwordElement = this.$refs.passwordInput as any;
      const innerInput = passwordElement.$el?.querySelector('input[type="password"]') ||
                        passwordElement.$el?.querySelector('input');
      if (innerInput) {
        innerInput.focus();
      }
    }
  }

  get referer(): string | null {
    const r = this.$route.query.referer;
    return typeof r === 'string' ? r : null;
  }

  /**
   * Parse OIDC callback parameters from window.location.search
   * This is needed because Vue Router hash mode only sees params after the #,
   * but OIDC providers return params in the main URL query string before the #
   */
  private getOidcCallbackParams(): { code: string | null, state: string | null, error: string | null, errorDescription: string | null } {
    const urlParams = new URLSearchParams(window.location.search);
    return {
      code: urlParams.get('code') || this.$route.query.code as string | null,
      state: urlParams.get('state') || this.$route.query.state as string | null,
      error: urlParams.get('error') || this.$route.query.error as string | null,
      errorDescription: urlParams.get('error_description') || this.$route.query.error_description as string | null,
    };
  }

  /**
   * Clean up the URL after OIDC callback processing
   * Removes the query params from the main URL while preserving the hash
   */
  private cleanupOidcUrlParams() {
    if (window.location.search && window.history.replaceState) {
      const cleanUrl = window.location.origin + window.location.pathname + window.location.hash;
      window.history.replaceState({}, document.title, cleanUrl);
    }
  }

  private async handleOidcError() {
    this.auth.setLoading(false);
    this.auth.setOidcCallbackLoading(false);
    
    // Get error params from window.location.search (not route query)
    // because hash-mode routing doesn't see params before the #
    const oidcParams = this.getOidcCallbackParams();
    const error = oidcParams.error || '';
    const errorDescription = oidcParams.errorDescription || '';
    
    const { userMessage, isRetryable, error: oidcError } = await this.auth.parseOidcError(error, errorDescription);
    
    this.displayAlert(userMessage);
    
    // Clean up URL after processing error
    this.cleanupOidcUrlParams();
    
    if (isRetryable) {
      this.auth.showRetryOption(oidcError);
      this.auth.updateState({ 
        emailEntered: true, 
        showPassword: false 
      });
    } else {
      this.auth.resetToEmail();
    }
  }

  private async handleOidcCallback() {
    const debugMode = await this.isDebugMode();
    
    // Get OIDC params from window.location.search (not route query)
    // because hash-mode routing doesn't see params before the #
    const oidcParams = this.getOidcCallbackParams();
    
    if (debugMode) {
      debug('OIDC callback handling started - oidcParams: %O, window.location.search: %s', oidcParams, window.location.search);
    }
    
    this.auth.setOidcCallbackLoading(true);
    
    try {
      const stateString = oidcParams.state || '';
      if (debugMode) {
        debug('Parsing state from URL: %s', stateString);
      }
      
      // The oidc-client-ts library manages its own state format
      // Our custom state is embedded in the url_state parameter
      let stateInfo = null;
      
      // Parse the state string to extract our custom state
      const tokens = stateString.split(';') ?? [];
      if (debugMode) {
        debug('State tokens: %O', tokens);
      }
      
      if (tokens.length < 2) {
        debug('Invalid state format - expected at least 2 tokens, got: %d', tokens.length);
        throw new Error(`Invalid OIDC state: ${stateString}`);
      }
      
      const customState = tokens[1] ?? '';
      
      // Parse our custom state to get the provider and referer
      stateInfo = await this.auth.parseOidcState(customState);
      if (debugMode) {
        debug('Parsed state info: %O', stateInfo);
      }
      
      if (!stateInfo) {
        debug('Failed to parse state info from localStorage');
        throw new Error('Invalid OIDC state');
      }
      
      const { referer, provider } = stateInfo;
      if (debugMode) {
        debug('Using provider: %s, referer: %s', provider, referer);
      }
      
      const userManager = await createUserManager(provider);
      if (debugMode) {
        debug('User manager created');
      }
      
      // Pass the full URL so the library can parse params from the query string
      // (needed for hash-mode routing where params are before the #)
      const user = await userManager.signinRedirectCallback(window.location.href);
      if (debugMode) {
        debug('Signin callback successful - user: %O, access_token: %s', user.profile, user.access_token ? 'present' : 'missing');
      }
      
      await this.userState.handleOidcLogin(user, provider);
      if (debugMode) {
        debug('OIDC login handled successfully');
      }
      
      // Clean up URL after successful callback processing
      this.cleanupOidcUrlParams();
      
      const redirectPath = referer || '/applications';
      if (debugMode) {
        debug('Redirecting to: %s', redirectPath);
      }
      await CONTINUUM_UI.navigate(redirectPath);
      
    } catch (error: unknown) {
      debug('OIDC callback error: %O', error);
      if (debugMode) {
        debug('Error details: %O', {
          message: error instanceof Error ? error.message : 'Unknown error',
          stack: error instanceof Error ? error.stack : undefined,
          windowLocationSearch: window.location.search,
          oidcParams
        });
      }
      
      if (error instanceof Error) {
        this.displayAlert(`OIDC callback failed: ${error.message}`);
      } else {
        this.displayAlert('OIDC callback failed');
      }
      
      // Clean up URL even on error
      this.cleanupOidcUrlParams();
      
      this.auth.resetToEmail();
    } finally {
      if (debugMode) {
        debug('Cleaning up callback loading state');
      }
      this.auth.setOidcCallbackLoading(false);
      this.auth.setLoading(false);
    }
  }

  async handleLogin() {
    if (!this.isLoginValid || !this.isPasswordValid) {
      this.displayAlert('Login and Password are required');
      return;
    }

    this.auth.setLoading(true);
    try {
      await this.userState.authenticate(this.login, this.password);

      if (this.referer) {
        await CONTINUUM_UI.navigate(this.referer);
      } else {
        const redirectPath = this.$route.redirectedFrom?.fullPath;
        if (redirectPath && redirectPath !== "/") {
          await CONTINUUM_UI.navigate(redirectPath);
        } else {
          await CONTINUUM_UI.navigate('/applications');
        }
      }
    } catch (error: unknown) {
      debug('Authentication error: %O', error);
      if (error instanceof Error) {
        this.displayAlert(error.message)
      } else if (typeof error === 'string') {
        this.displayAlert(error)
      } else {
        this.displayAlert('Unknown login error')
      }
      
      this.auth.resetToEmail();
    } finally {
      this.auth.setLoading(false);
    }
  }

  private hideAlert() {
    this.toast.removeAllGroups();
  }

  private displayAlert(text: string) {
    this.toast.add({
      severity: 'error',
      summary: 'Error',
      detail: text,
      life: 30000
    });
  }

  private async handleEmailSubmit() {
    if (!this.isLoginValid) {
      this.displayAlert('Please enter a valid email address');
      return;
    }

    this.auth.setLoading(true);
    
    try {
      const authMethod = await this.auth.determineAuthMethod(this.login);
      
      if (authMethod.shouldUseOidc && authMethod.matchedProvider) {
        // Automatically redirect to OIDC login when provider is found
        await this.handleOidcLogin(authMethod.matchedProvider);
        return; // Exit early to prevent any further UI updates
      } else {
        // Either OIDC is disabled, no provider matched, or fallback is needed
        // Always show password form in these cases
        this.auth.updateState({
          emailEntered: true,
          showPassword: true,
          matchedProvider: null,
          providerDisplayName: '',
          showRetryOption: false,
          showErrorDetails: false
        });
      }
      
      this.$nextTick(() => {
        this.focusPasswordInput();
      });
    } catch (error) {
      debug('Error in email submit: %O', error);
      this.displayAlert('Error processing email. Please try again.');
    } finally {
      this.auth.setLoading(false);
    }
  }

  private getProviderIcon(provider: string): string {
    return this.auth.getProviderIcon(provider);
  }

  private resetToEmail() {
    this.auth.resetToEmail();
    this.$nextTick(() => {
      this.focusEmailInput();
    });
  }

  private retryOidcLogin() {
    this.auth.clearRetryOption();
    this.clearUrlErrorParams();
    
    if (this.state.matchedProvider && this.login) {
      this.handleOidcLogin(this.state.matchedProvider);
    } else {
      this.auth.resetToEmail();
    }
  }

  private usePasswordInstead() {
    this.auth.clearRetryOption();
    this.auth.updateState({
      emailEntered: true,
      showPassword: true,
      matchedProvider: null,
      providerDisplayName: '',
      showRetryOption: false,
      showErrorDetails: false
    });
    this.clearUrlErrorParams();
    
    this.$nextTick(() => {
      this.focusPasswordInput();
    });
  }

  private goBackToEmail() {
    this.auth.resetToEmail();
    this.clearUrlErrorParams();
    
    this.$nextTick(() => {
      this.focusEmailInput();
    });
  }

  private clearErrorAndReset() {
    this.auth.resetAfterError();
    this.clearUrlErrorParams();
    
    this.$nextTick(() => {
      this.focusEmailInput();
    });
  }

  private toggleErrorDetails() {
    this.auth.toggleErrorDetails();
  }

  private clearUrlErrorParams() {
    if (this.$route.query.error || this.$route.query.error_description) {
      const newQuery = { ...this.$route.query };
      delete newQuery.error;
      delete newQuery.error_description;
      this.$router.replace({ query: newQuery });
    }
  }

  private async handleOidcLogin(provider: string) {
    try {
      const userManager = await createUserManager(provider);
      const state = await this.auth.createOidcState(this.referer, provider);
      
      const signinOptions: any = { url_state: state };
      
      if (this.login) {
        signinOptions.login_hint = this.login;
        
        const emailDomain = this.login.split('@')[1];
        if (emailDomain) {
          signinOptions.domain_hint = emailDomain;
        }
      }
      
      await userManager.signinRedirect(signinOptions);
      
    } catch (error) {
      this.displayAlert(`OIDC login failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
      this.auth.resetToEmail();
    }
  }

}
</script>

<style scoped>
/* Layout & theming live in src/pages/auth-pages.css (shared with Signup, VerifyEmail). */
</style>