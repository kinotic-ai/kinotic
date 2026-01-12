import { configService } from './config';

export interface OidcError {
  error: string;
  description?: string;
}

export interface AuthenticationState {
  emailEntered: boolean;
  showPassword: boolean;
  matchedProvider: string | null;
  providerDisplayName: string;
  showRetryOption: boolean;
  showErrorDetails: boolean;
  loading: boolean;
  oidcCallbackLoading: boolean;
  password: string;
  authenticationSuccess: boolean;
}

export interface ProviderConfig {
  provider: string;
  displayName: string;
  icon: string;
  enabled: boolean;
}

export class AuthenticationManager {
  private enabledProviders: Set<string> = new Set();
  private basicAuthEnabled: boolean = false;
  private configLoaded: boolean = false;

  constructor() {
    this.loadConfiguration();
  }

  /**
   * Load authentication configuration
   */
  async loadConfiguration(): Promise<void> {
    try {
      this.basicAuthEnabled = await configService.isBasicAuthEnabled();
      const enabledProviders = await configService.getEnabledOidcProviders();
      
      this.enabledProviders.clear();
      for (const provider of enabledProviders) {
        this.enabledProviders.add(provider.provider);
      }
      
      this.configLoaded = true;
    } catch (error) {
      console.error('Failed to load configuration:', error);
      this.basicAuthEnabled = true;
      this.configLoaded = true;
    }
  }

  /**
   * Check if configuration is loaded
   */
  isConfigLoaded(): boolean {
    return this.configLoaded;
  }

  /**
   * Check if basic auth is enabled
   */
  isBasicAuthEnabled(): boolean {
    return this.basicAuthEnabled;
  }

  /**
   * Check if any OIDC providers are enabled
   */
  hasEnabledOidcProviders(): boolean {
    return this.enabledProviders.size > 0;
  }

  /**
   * Find OIDC provider by email domain
   */
  async findProviderByDomain(email: string): Promise<string | null> {
    try {
      const provider = await configService.findProviderByEmailDomain(email);
      return provider?.provider || null;
    } catch (error) {
      console.warn(`Failed to find OIDC config for domain ${email}`, error);
      return null;
    }
  }

  /**
   * Get provider display name
   */
  async getProviderDisplayName(provider: string): Promise<string> {
    try {
      const providerConfig = await configService.getOidcProviderByName(provider);
      return providerConfig?.displayName || provider;
    } catch (error) {
      console.warn(`Failed to get display name for provider ${provider}:`, error);
      return provider;
    }
  }

  /**
   * Get provider icon path
   */
  getProviderIcon(provider: string): string {
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

  /**
   * Determine authentication method based on email
   */
  async determineAuthMethod(email: string): Promise<{
    shouldUseOidc: boolean;
    matchedProvider: string | null;
    providerDisplayName: string;
    fallbackToBasicAuth: boolean;
  }> {
    const shouldUseOidc = await configService.isOidcEnabled();
    
    if (!shouldUseOidc) {
      return {
        shouldUseOidc: false,
        matchedProvider: null,
        providerDisplayName: '',
        fallbackToBasicAuth: true
      };
    }

    const matchedProvider = await this.findProviderByDomain(email);
    
    if (matchedProvider) {
      const providerDisplayName = await this.getProviderDisplayName(matchedProvider);
      return {
        shouldUseOidc: true,
        matchedProvider,
        providerDisplayName,
        fallbackToBasicAuth: false
      };
    } else {
      return {
        shouldUseOidc: true,
        matchedProvider: null,
        providerDisplayName: '',
        fallbackToBasicAuth: true
      };
    }
  }

  /**
   * Parse and categorize OIDC errors from URL parameters
   */
  parseOidcError(error: string, errorDescription?: string): {
    userMessage: string;
    isRetryable: boolean;
    error: OidcError;
  } {
    const decodedErrorDescription = errorDescription ? decodeURIComponent(errorDescription) : '';
    
    let userMessage = 'OIDC login failed';
    let isRetryable = false;
    
    if (error === 'invalid_request') {
      if (decodedErrorDescription.includes('AADSTS50194')) {
        userMessage = 'Microsoft Azure configuration error: Application is not configured as multi-tenant. Please contact your administrator.';
      } else if (decodedErrorDescription.includes('redirect_uri_mismatch')) {
        userMessage = 'OIDC configuration error: Redirect URI mismatch. Please contact your administrator.';
      } else {
        userMessage = `OIDC configuration error: ${decodedErrorDescription}`;
        isRetryable = true;
      }
    } else if (error === 'access_denied') {
      userMessage = 'Access denied by OIDC provider. Please try again or contact your administrator.';
      isRetryable = true;
    } else if (error === 'unauthorized_client') {
      userMessage = 'OIDC client not authorized. Please contact your administrator.';
      isRetryable = true;
    } else if (error === 'unsupported_response_type') {
      userMessage = 'OIDC response type not supported. Please contact your administrator.';
    } else if (error === 'server_error') {
      userMessage = 'OIDC server error. Please try again later.';
      isRetryable = true;
    } else if (error === 'temporarily_unavailable') {
      userMessage = 'OIDC service temporarily unavailable. Please try again later.';
      isRetryable = true;
    } else if (decodedErrorDescription) {
      userMessage = `OIDC login failed: ${decodedErrorDescription}`;
      isRetryable = true;
    }
    
    return {
      userMessage,
      isRetryable,
      error: {
        error,
        description: decodedErrorDescription
      }
    };
  }

  /**
   * Create initial authentication state
   */
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

  /**
   * Reset state to email input
   */
  resetToEmail(state: AuthenticationState): AuthenticationState {
    return {
      ...state,
      emailEntered: false,
      showPassword: false,
      matchedProvider: null,
      providerDisplayName: '',
      showRetryOption: false,
      showErrorDetails: false,
      password: '',
      authenticationSuccess: false
    };
  }

  /**
   * Reset state to password input
   */
  resetToPassword(state: AuthenticationState, matchedProvider: string | null, providerDisplayName: string): AuthenticationState {
    return {
      ...state,
      emailEntered: true,
      showPassword: true,
      matchedProvider,
      providerDisplayName,
      showRetryOption: false,
      showErrorDetails: false
    };
  }

  /**
   * Show provider selection state
   */
  showProviderSelection(state: AuthenticationState, matchedProvider: string, providerDisplayName: string): AuthenticationState {
    return {
      ...state,
      emailEntered: true,
      showPassword: false,
      matchedProvider,
      providerDisplayName,
      showRetryOption: false,
      showErrorDetails: false
    };
  }

  /**
   * Show retry option state
   */
  showRetryOption(state: AuthenticationState): AuthenticationState {
    return {
      ...state,
      showRetryOption: true,
      showErrorDetails: false,
      loading: false,
      oidcCallbackLoading: false
    };
  }

  /**
   * Clear retry option state
   */
  clearRetryOption(state: AuthenticationState): AuthenticationState {
    return {
      ...state,
      showRetryOption: false,
      showErrorDetails: false
    };
  }

  /**
   * Toggle error details
   */
  toggleErrorDetails(state: AuthenticationState): AuthenticationState {
    return {
      ...state,
      showErrorDetails: !state.showErrorDetails
    };
  }

  /**
   * Set loading state
   */
  setLoading(state: AuthenticationState, loading: boolean): AuthenticationState {
    return {
      ...state,
      loading
    };
  }

  /**
   * Set OIDC callback loading state
   */
  setOidcCallbackLoading(state: AuthenticationState, loading: boolean): AuthenticationState {
    return {
      ...state,
      oidcCallbackLoading: loading
    };
  }

  /**
   * Clear OIDC state from localStorage
   */
  clearOidcState(): void {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith('oidc.')) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach(key => localStorage.removeItem(key));
  }

  /**
   * Create OIDC state object for redirect
   */
  createOidcState(referer: string | null, provider: string): string {
    const stateObj = {
      referer,
      provider
    };
    const encodedState = btoa(JSON.stringify(stateObj));
    
    // Store the state data in localStorage so it can be retrieved during callback
    const stateKey = `oidc.${encodedState}`;
    const stateData = {
      data: encodedState,
      timestamp: Date.now()
    };
    localStorage.setItem(stateKey, JSON.stringify(stateData));
    
    return encodedState;
  }

  /**
   * Parse OIDC state from URL
   */
  parseOidcState(state: string): { referer: string | null; provider: string } | null {
    try {
      const stateKey = `oidc.${state}`;
      const storedState = localStorage.getItem(stateKey);
      
      if (storedState) {
        const sessionState = JSON.parse(storedState);
        
        // Check if state has expired (24 hours)
        const stateAge = Date.now() - sessionState.timestamp;
        if (stateAge > 24 * 60 * 60 * 1000) {
          console.warn('OIDC state has expired');
          localStorage.removeItem(stateKey);
          throw new Error("OIDC state has expired")
        }
        
        const stateObj = JSON.parse(atob(sessionState.data));
        
        // Clean up the state from localStorage after successful parsing
        localStorage.removeItem(stateKey);
        
        return {
          referer: stateObj.referer || null,
          provider: stateObj.provider || 'keycloak'
        };
      }
    } catch (error) {
      console.warn('Failed to parse OIDC state:', error);
    }
    
    return null;
  }
}
