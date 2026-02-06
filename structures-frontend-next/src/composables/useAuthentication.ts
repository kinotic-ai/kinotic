import { ref, computed, readonly } from 'vue';
import { AuthenticationManager, type AuthenticationState, type OidcError } from '@/util/AuthenticationManager';

export function useAuthentication() {
  const authManager = new AuthenticationManager();
  
  // Reactive state
  const state = ref<AuthenticationState>(authManager.createInitialState());
  const login = ref('');
  const password = ref('');
  const currentOidcError = ref<OidcError | null>(null);

  // Computed properties
  const isConfigLoaded = computed(() => authManager.isConfigLoaded());
  const isBasicAuthEnabled = computed(() => authManager.isBasicAuthEnabled());
  const hasEnabledOidcProviders = computed(() => authManager.hasEnabledOidcProviders());
  const shouldShowLoginForm = computed(() => !state.value.oidcCallbackLoading);

  // State management methods
  const updateState = (newState: Partial<AuthenticationState>) => {
    state.value = { ...state.value, ...newState };
  };

  const resetToEmail = () => {
    const newState = authManager.resetToEmail(state.value);
    updateState(newState);
    password.value = '';
    currentOidcError.value = null;
  };

  const resetToPassword = (matchedProvider: string | null, providerDisplayName: string) => {
    const newState = authManager.resetToPassword(state.value, matchedProvider, providerDisplayName);
    updateState(newState);
  };

  const showProviderSelection = (matchedProvider: string, providerDisplayName: string) => {
    const newState = authManager.showProviderSelection(state.value, matchedProvider, providerDisplayName);
    updateState(newState);
  };

  const showRetryOption = (error: OidcError) => {
    const newState = authManager.showRetryOption(state.value);
    updateState(newState);
    currentOidcError.value = error;
  };

  const clearRetryOption = () => {
    const newState = authManager.clearRetryOption(state.value);
    updateState(newState);
    currentOidcError.value = null;
  };

  const toggleErrorDetails = () => {
    const newState = authManager.toggleErrorDetails(state.value);
    updateState(newState);
  };

  const setLoading = (loading: boolean) => {
    const newState = authManager.setLoading(state.value, loading);
    updateState(newState);
  };

  const setOidcCallbackLoading = (loading: boolean) => {
    const newState = authManager.setOidcCallbackLoading(state.value, loading);
    updateState(newState);
  };

  // Authentication logic methods
  const determineAuthMethod = async (email: string) => {
    return await authManager.determineAuthMethod(email);
  };

  const findProviderByDomain = async (email: string) => {
    return await authManager.findProviderByDomain(email);
  };

  const getProviderDisplayName = async (provider: string) => {
    return await authManager.getProviderDisplayName(provider);
  };

  const getProviderIcon = (provider: string) => {
    return authManager.getProviderIcon(provider);
  };

  const parseOidcError = (error: string, errorDescription?: string) => {
    return authManager.parseOidcError(error, errorDescription);
  };

  // OIDC state management
  const clearOidcState = () => {
    authManager.clearOidcState();
  };

  const createOidcState = (referer: string | null, provider: string) => {
    return authManager.createOidcState(referer, provider);
  };

  const parseOidcState = (state: string) => {
    return authManager.parseOidcState(state);
  };

  // Create initial state
  const createInitialState = () => {
    return authManager.createInitialState();
  };

  // Form validation
  const isLoginValid = computed(() => !!login.value);
  const isPasswordValid = computed(() => !!password.value);

  // Utility methods
  const clearForm = () => {
    login.value = '';
    password.value = '';
    resetToEmail();
  };

  const resetAfterError = () => {
    clearRetryOption();
    currentOidcError.value = null;
    clearOidcState();
  };

  return {
    // State
    state: readonly(state),
    login,
    password,
    currentOidcError,
    
    // Computed
    isConfigLoaded,
    isBasicAuthEnabled,
    hasEnabledOidcProviders,
    shouldShowLoginForm,
    isLoginValid,
    isPasswordValid,
    
    // State management
    updateState,
    resetToEmail,
    resetToPassword,
    showProviderSelection,
    showRetryOption,
    clearRetryOption,
    toggleErrorDetails,
    setLoading,
    setOidcCallbackLoading,
    
    // Authentication logic
    determineAuthMethod,
    findProviderByDomain,
    getProviderDisplayName,
    getProviderIcon,
    parseOidcError,
    
    // OIDC state management
    clearOidcState,
    createOidcState,
    parseOidcState,
    createInitialState,
    
    // Utility
    clearForm,
    resetAfterError
  };
}
