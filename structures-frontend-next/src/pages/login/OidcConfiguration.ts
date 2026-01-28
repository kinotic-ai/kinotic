import { type UserManagerSettings, WebStorageStateStore, UserManager } from 'oidc-client-ts';
import { configService } from '@/util/config';

export interface OidcProviderConfig extends Partial<UserManagerSettings> {
  enabled: boolean;
  clientId: string;
  clientSecret?: string;
  authority: string;
  redirectUri: string;
  postLogoutRedirectUri?: string;
  silentRedirectUri?: string;
  loadUserInfo?: boolean;
  additionalScopes?: string;
  // Additional OIDC parameters for better UX
  enableEmailPreFill?: boolean;
  enableDomainHint?: boolean;
  metadata?: any;
  publicClient?: PublicClientConfig;
}

export interface PublicClientConfig {
  isPublicClient: boolean;
  responseType: string;
  responseMode: string;
}

export interface OidcConfiguration {
  defaultProvider: string;
  providers: Record<string, OidcProviderConfig>;
  defaultSettings: Partial<UserManagerSettings>;
}

const DEFAULT_SETTINGS: Partial<UserManagerSettings> = {
  response_type: 'code',
  response_mode: 'query',
  scope: 'openid profile email offline_access',
  loadUserInfo: true,
  monitorSession: true,
  automaticSilentRenew: true,
  accessTokenExpiringNotificationTimeInSeconds: 60,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  stateStore: new WebStorageStateStore({ store: window.localStorage }),
};

// Create a function to get the OIDC configuration dynamically
export async function getOidcConfiguration(): Promise<OidcConfiguration> {
  const oidcConfig = await configService.loadConfig();
  const isDebug = await configService.isDebugEnabled();

  // Debug: Log configuration for troubleshooting
  if (isDebug) {
    console.log('OIDC Configuration:', oidcConfig);
  }

  // Get all enabled OIDC providers
  const enabledProviders = await configService.getEnabledOidcProviders();
  
  // Build dynamic providers configuration
  const providers: Record<string, OidcProviderConfig> = {};
  
  for (const provider of enabledProviders) {
    providers[provider.provider] = {
      enabled: provider.enabled,
      clientId: provider.clientId,
      clientSecret: '',
      authority: provider.authority,
      redirectUri: provider.redirectUri,
      postLogoutRedirectUri: provider.postLogoutRedirectUri,
      silentRedirectUri: provider.silentRedirectUri,
      loadUserInfo: true,
      additionalScopes: provider.additionalScopes ?? '',
      publicClient: {
        isPublicClient: true,
        responseType: 'code',
        responseMode: 'query'
      },
      // Add custom metadata if available
      ...(provider.metadata && {
        metadata: {
          authorizationEndpoint: provider.metadata.authorization_endpoint,
          tokenEndpoint: provider.metadata.token_endpoint,
          userinfoEndpoint: provider.metadata.userinfo_endpoint,
          endSessionEndpoint: provider.metadata.end_session_endpoint,
          jwksUri: provider.metadata.jwks_uri,
        }
      })
    };
  }

  // Determine default provider (use first enabled provider, or 'keycloak' as fallback)
  const defaultProvider = enabledProviders.length > 0 ? enabledProviders[0].provider : 'keycloak';

  return {
    defaultProvider,
    defaultSettings: DEFAULT_SETTINGS,
    providers
  };
}

export async function getProviderConfig(providerName: string): Promise<OidcProviderConfig | null> {
  const config = await getOidcConfiguration();
  return config.providers[providerName] || null;
}

export async function createUserManagerSettings(providerName: string): Promise<UserManagerSettings> {
  const config = await getOidcConfiguration();
  const providerConfig = config.providers[providerName];
  
  if (!providerConfig) {
    throw new Error(`Provider configuration not found for: ${providerName}`);
  }

  if (!providerConfig.enabled) {
    throw new Error(`Provider ${providerName} is not enabled`);
  }

  // Merge default settings with provider-specific settings
  const settings: UserManagerSettings = {
    ...config.defaultSettings,
    authority: providerConfig.authority,
    client_id: providerConfig.clientId,
    redirect_uri: providerConfig.redirectUri,
    post_logout_redirect_uri: providerConfig.postLogoutRedirectUri,
    silent_redirect_uri: providerConfig.silentRedirectUri,
    loadUserInfo: providerConfig.loadUserInfo ?? true,
    // Ensure required fields are set
    response_type: 'code',
    response_mode: 'query',
    scope: `openid profile email offline_access ${providerConfig.additionalScopes ?? ''}`,
    // Add custom metadata if available
    ...(providerConfig.metadata && {
      metadata: {
        authorization_endpoint: providerConfig.metadata.authorizationEndpoint,
        token_endpoint: providerConfig.metadata.tokenEndpoint,
        userinfo_endpoint: providerConfig.metadata.userinfoEndpoint,
        end_session_endpoint: providerConfig.metadata.endSessionEndpoint,
        jwks_uri: providerConfig.metadata.jwksUri,
      }
    })
  };

  // Log settings for debugging
  console.log('UserManager settings created:', {
    authority: settings.authority,
    client_id: settings.client_id,
    redirect_uri: settings.redirect_uri,
    enableEmailPreFill: providerConfig.enableEmailPreFill,
    enableDomainHint: providerConfig.enableDomainHint
  });

  return settings;
}

export async function createUserManager(providerName: string): Promise<UserManager> {
  const settings = await createUserManagerSettings(providerName);
  return new UserManager(settings);
}

// Helper function to get all available provider names
export async function getAvailableProviders(): Promise<string[]> {
  const enabledProviders = await configService.getEnabledOidcProviders();
  return enabledProviders.map(provider => provider.provider);
}

// Helper function to check if a specific provider is enabled
export async function isProviderEnabled(providerName: string): Promise<boolean> {
  const provider = await configService.getOidcProviderByName(providerName);
  return provider?.enabled ?? false;
} 