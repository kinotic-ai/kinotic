import { User, UserManager } from 'oidc-client-ts'
import { createUserManagerSettings } from '@/pages/login/OidcConfiguration'
import Cookies from 'js-cookie'

/**
 * OidcSessionManager maintains a persistent UserManager instance with event listeners
 * to handle automatic token refresh via oidc-client-ts's automaticSilentRenew feature.
 * 
 * When tokens are refreshed, it updates the cookies so that:
 * - Playgrounds can use the fresh token
 * - Page refresh can reconnect with the stored token
 */
class OidcSessionManager {
    private userManager: UserManager | null = null
    private onRefreshFailed: (() => void) | null = null
    private boundHandlers: {
        userLoaded: ((user: User) => void) | null
        silentRenewError: ((error: Error) => void) | null
        accessTokenExpired: (() => void) | null
    } = {
        userLoaded: null,
        silentRenewError: null,
        accessTokenExpired: null
    }
    
    /**
     * Initialize with a provider after OIDC login.
     * Sets up event listeners for automatic token refresh.
     */
    async initialize(
        provider: string,
        onRefreshFailed: () => void
    ): Promise<void> {
        await this.cleanup()
        
        const settings = await createUserManagerSettings(provider)
        this.userManager = new UserManager(settings)
        this.onRefreshFailed = onRefreshFailed
        
        // Create bound handlers so we can properly remove them later
        this.boundHandlers.userLoaded = (user: User) => {
            console.log('Token refreshed automatically')
            this.updateCookies(user)
        }
        
        this.boundHandlers.silentRenewError = (error: Error) => {
            console.error('Silent renew failed:', error)
            this.onRefreshFailed?.()
        }
        
        this.boundHandlers.accessTokenExpired = () => {
            console.warn('Access token expired')
            this.onRefreshFailed?.()
        }
        
        // Listen for token refresh events
        this.userManager.events.addUserLoaded(this.boundHandlers.userLoaded)
        this.userManager.events.addSilentRenewError(this.boundHandlers.silentRenewError)
        this.userManager.events.addAccessTokenExpired(this.boundHandlers.accessTokenExpired)
    }
    
    /**
     * Cleanup on logout - removes event listeners and clears state
     */
    async cleanup(): Promise<void> {
        if (this.userManager) {
            // Remove event listeners using the bound handlers
            if (this.boundHandlers.userLoaded) {
                this.userManager.events.removeUserLoaded(this.boundHandlers.userLoaded)
            }
            if (this.boundHandlers.silentRenewError) {
                this.userManager.events.removeSilentRenewError(this.boundHandlers.silentRenewError)
            }
            if (this.boundHandlers.accessTokenExpired) {
                this.userManager.events.removeAccessTokenExpired(this.boundHandlers.accessTokenExpired)
            }
            
            // Stop any pending silent renew
            this.userManager.stopSilentRenew()
            
            this.userManager = null
        }
        
        this.boundHandlers = {
            userLoaded: null,
            silentRenewError: null,
            accessTokenExpired: null
        }
        this.onRefreshFailed = null
    }
    
    /**
     * Get the UserManager for signin operations
     */
    getUserManager(): UserManager | null {
        return this.userManager
    }
    
    /**
     * Update cookies with fresh tokens from the refreshed user
     */
    private updateCookies(user: User): void {
        let tokenToUse = user.access_token
        
        // Some providers (like Microsoft) return opaque access tokens
        // In that case, use the ID token instead
        if (!this.isValidJWT(user.access_token) && user.id_token) {
            console.log('Access token is not a valid JWT, using ID token')
            tokenToUse = user.id_token
        }
        
        Cookies.set('token', tokenToUse, {
            sameSite: 'strict',
            secure: true,
            expires: new Date(user.expires_at! * 1000)
        })
        
        if (user.refresh_token) {
            const refreshExpiry = this.parseJwtExpiry(user.refresh_token)
            Cookies.set('oidc_refresh_token', user.refresh_token, {
                sameSite: 'strict',
                secure: true,
                expires: refreshExpiry ?? new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
            })
        }
    }
    
    /**
     * Check if a token is a valid JWT (has 3 parts separated by dots)
     */
    private isValidJWT(token: string): boolean {
        try {
            const parts = token.split('.')
            return parts.length === 3
        } catch {
            return false
        }
    }
    
    /**
     * Parse the expiry date from a JWT token
     */
    private parseJwtExpiry(token: string): Date | null {
        try {
            const parts = token.split('.')
            if (parts.length !== 3) return null
            const payload = JSON.parse(atob(parts[1]))
            return payload.exp ? new Date(payload.exp * 1000) : null
        } catch {
            return null
        }
    }
}

export const oidcSessionManager = new OidcSessionManager()
