import { AuthenticationManager, type AuthenticationState, type OidcError } from './AuthenticationManager';
import { configService } from './config';
import { createDebug } from './debug';

const debug = createDebug('authentication-service');

export class AuthenticationService {
  private authManager: AuthenticationManager | null = null;
  private _state: AuthenticationState;
  private _login: string = '';
  private _password: string = '';
  private _currentOidcError: OidcError | null = null;

  constructor() {
    this._state = {
      emailEntered: false,
      showPassword: false,
      matchedProvider: null,
      providerDisplayName: '',
      showRetryOption: false,
      showErrorDetails: false,
      loading: false,
      oidcCallbackLoading: false,
      password: '',
      authenticationSuccess: false
    };
  }

  private async getAuthManager(): Promise<AuthenticationManager> {
    if (!this.authManager) {
      this.authManager = new AuthenticationManager();
      await this.authManager.loadConfiguration();
    }
    return this.authManager;
  }

  get state(): AuthenticationState {
    return this._state;
  }

  get login(): string {
    return this._login;
  }

  set login(value: string) {
    this._login = value;
  }

  get password(): string {
    return this._password;
  }

  set password(value: string) {
    this._password = value;
  }

  get currentOidcError(): OidcError | null {
    return this._currentOidcError;
  }

  get shouldShowLoginForm(): boolean {
    return !this._state.oidcCallbackLoading;
  }

  get isLoginValid(): boolean {
    return !!this._login;
  }

  get isPasswordValid(): boolean {
    return !!this._password;
  }

  async isConfigLoaded(): Promise<boolean> {
    try {
      const authManager = await this.getAuthManager();
      return authManager.isConfigLoaded();
    } catch (error) {
      debug('Failed to get config status: %O', error);
      return false;
    }
  }

  async isBasicAuthEnabled(): Promise<boolean> {
    try {
      const authManager = await this.getAuthManager();
      return authManager.isBasicAuthEnabled();
    } catch (error) {
      debug('Failed to check basic auth: %O', error);
      return true;
    }
  }

  async hasEnabledOidcProviders(): Promise<boolean> {
    try {
      const authManager = await this.getAuthManager();
      return authManager.hasEnabledOidcProviders();
    } catch (error) {
      debug('Failed to check OIDC providers: %O', error);
      return false;
    }
  }

  updateState(newState: Partial<AuthenticationState>): void {
    debug('updateState: %O', newState);
    this._state = { ...this._state, ...newState };
  }

  resetToEmail(): void {
    if (this.authManager) {
      const newState = this.authManager.resetToEmail(this._state);
      this.updateState(newState);
    } else {
      this.updateState({
        emailEntered: false,
        showPassword: false,
        matchedProvider: null,
        providerDisplayName: '',
        showRetryOption: false,
        showErrorDetails: false,
        authenticationSuccess: false
      });
    }
    this._password = '';
    this._currentOidcError = null;
  }

  resetToPassword(matchedProvider: string | null, providerDisplayName: string): void {
    debug('resetToPassword: provider=%s, displayName=%s', matchedProvider, providerDisplayName);
    
    if (this.authManager) {
      const newState = this.authManager.resetToPassword(this._state, matchedProvider, providerDisplayName);
      this.updateState(newState);
    } else {
      this.updateState({
        emailEntered: true,
        showPassword: true,
        matchedProvider,
        providerDisplayName,
        showRetryOption: false,
        showErrorDetails: false
      });
    }
  }

  showProviderSelection(matchedProvider: string, providerDisplayName: string): void {
    if (this.authManager) {
      const newState = this.authManager.showProviderSelection(this._state, matchedProvider, providerDisplayName);
      this.updateState(newState);
    } else {
      this.updateState({
        emailEntered: true,
        showPassword: false,
        matchedProvider,
        providerDisplayName,
        showRetryOption: false,
        showErrorDetails: false
      });
    }
  }

  showRetryOption(error: OidcError): void {
    if (this.authManager) {
      const newState = this.authManager.showRetryOption(this._state);
      this.updateState(newState);
    } else {
      this.updateState({
        showRetryOption: true,
        showErrorDetails: false,
        loading: false,
        oidcCallbackLoading: false
      });
    }
    this._currentOidcError = error;
  }

  clearRetryOption(): void {
    if (this.authManager) {
      const newState = this.authManager.clearRetryOption(this._state);
      this.updateState(newState);
    } else {
      this.updateState({
        showRetryOption: false,
        showErrorDetails: false
      });
    }
    this._currentOidcError = null;
  }

  toggleErrorDetails(): void {
    if (this.authManager) {
      const newState = this.authManager.toggleErrorDetails(this._state);
      this.updateState(newState);
    } else {
      this.updateState({
        showErrorDetails: !this._state.showErrorDetails
      });
    }
  }

  setLoading(loading: boolean): void {
    if (this.authManager) {
      const newState = this.authManager.setLoading(this._state, loading);
      this.updateState(newState);
    } else {
      this.updateState({ loading });
    }
  }

  setOidcCallbackLoading(loading: boolean): void {
    if (this.authManager) {
      const newState = this.authManager.setOidcCallbackLoading(this._state, loading);
      this.updateState(newState);
    } else {
      this.updateState({ oidcCallbackLoading: loading });
    }
  }

  async determineAuthMethod(email: string) {
    const authManager = await this.getAuthManager();
    return await authManager.determineAuthMethod(email);
  }

  async findProviderByDomain(email: string) {
    const authManager = await this.getAuthManager();
    return await authManager.findProviderByDomain(email);
  }

  async getProviderDisplayName(provider: string) {
    const authManager = await this.getAuthManager();
    return await authManager.getProviderDisplayName(provider);
  }

  getProviderIcon(provider: string): string {
    if (this.authManager) {
      return this.authManager.getProviderIcon(provider);
    } else {
      const iconMap: Record<string, string> = {
        okta: '@/assets/okta-icon.svg',
        keycloak: '@/assets/keycloak-icon.svg',
        google: '@/assets/google-icon.svg',
        microsoft: '@/assets/microsoft_online-icon.svg',
        microsoftSocial: '@/assets/microsoft_online-icon.svg',
        github: '@/assets/github-icon.svg',
        apple: '@/assets/apple-logo.svg'
      };
      return iconMap[provider] || '@/assets/input-hide.svg';
    }
  }

  async parseOidcError(error: string, errorDescription?: string) {
    const authManager = await this.getAuthManager();
    return authManager.parseOidcError(error, errorDescription);
  }

  clearOidcState(): void {
    if (this.authManager) {
      this.authManager.clearOidcState();
    } else {
      const keysToRemove: string[] = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('oidc.')) {
          keysToRemove.push(key);
        }
      }
      keysToRemove.forEach(key => localStorage.removeItem(key));
    }
  }

  async createOidcState(referer: string | null, provider: string): Promise<string> {
    const authManager = await this.getAuthManager();
    return authManager.createOidcState(referer, provider);
  }

  async parseOidcState(state: string) {
    const authManager = await this.getAuthManager();
    return authManager.parseOidcState(state);
  }

  createInitialState(): AuthenticationState {
    return {
      emailEntered: false,
      showPassword: false,
      matchedProvider: null,
      providerDisplayName: '',
      showRetryOption: false,
      showErrorDetails: false,
      loading: false,
      oidcCallbackLoading: false,
      password: '',
      authenticationSuccess: false
    };
  }

  clearForm(): void {
    this._login = '';
    this._password = '';
    this.resetToEmail();
  }

  resetAfterError(): void {
    this.clearRetryOption();
    this._currentOidcError = null;
    this.clearOidcState();
  }

  async checkBasicAuthEnabled(): Promise<boolean> {
    try {
      return await configService.isBasicAuthEnabled();
    } catch (error) {
      debug('Failed to check basic auth: %O', error);
      return true;
    }
  }

  async checkOidcEnabled(): Promise<boolean> {
    try {
      return await configService.isOidcEnabled();
    } catch (error) {
      debug('Failed to check OIDC: %O', error);
      return false;
    }
  }

  async isDebugEnabled(): Promise<boolean> {
    try {
      return await configService.isDebugEnabled();
    } catch (error) {
      debug('Failed to check debug mode: %O', error);
      return false;
    }
  }
}
