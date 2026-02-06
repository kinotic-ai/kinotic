# Authentication Utilities

This directory contains utilities that simplify authentication logic and make the Login component more maintainable.

## Files

### AuthenticationManager.ts
A comprehensive class that handles all authentication-related logic including:
- OIDC provider management
- Authentication method determination
- Error parsing and categorization
- State management utilities
- OIDC state handling

### AuthenticationService.ts
A Vue 2 compatible service class that provides authentication state management:
- Reactive state updates
- Computed properties for form validation
- State transition methods
- Integration with AuthenticationManager
- Designed for Vue 2 class components

### useAuthentication.ts (Composable)
A Vue 3 composable that provides reactive authentication state management:
- Reactive state updates
- Computed properties for form validation
- State transition methods
- Integration with AuthenticationManager
- **Note**: This is for Vue 3 Composition API components

## Benefits of the New Architecture

### Before (Login.vue)
- **824 lines** of complex authentication logic
- Mixed concerns (UI, business logic, state management)
- Difficult to test individual pieces
- Hard to maintain and debug
- Complex state management scattered throughout the component

### After (Refactored)
- **~300 lines** in Login.vue (UI only)
- **~200 lines** in AuthenticationManager.ts (business logic)
- **~200 lines** in AuthenticationService.ts (Vue 2 state management)
- **~150 lines** in useAuthentication.ts (Vue 3 composable)
- Clear separation of concerns
- Easier to test individual components
- More maintainable and readable code

## Usage Examples

### Vue 2 Class Components (Recommended for this project)
```typescript
import { AuthenticationService } from '@/util/AuthenticationService';

export default class Login extends Vue {
  private auth = new AuthenticationService();
  
  // Access state
  get state() { return this.auth.state; }
  get login() { return this.auth.login; }
  set login(value: string) { this.auth.login = value; }
  
  // Use computed properties
  get isConfigLoaded() { return this.auth.isConfigLoaded; }
  get isBasicAuthEnabled() { return this.auth.isBasicAuthEnabled; }
  
  // State management
  resetForm() { this.auth.resetToEmail(); }
  showPassword() { this.auth.resetToPassword(null, ''); }
  
  // Authentication logic
  async handleEmail(email: string) {
    const method = await this.auth.determineAuthMethod(email);
    if (method.shouldUseOidc) {
      // Handle OIDC
    } else {
      // Handle basic auth
    }
  }
}
```

### Vue 3 Composition API
```typescript
import { useAuthentication } from '@/composables/useAuthentication';

export default {
  setup() {
    const auth = useAuthentication();
    
    // Access state
    const { state, login, password } = auth;
    
    // Use computed properties
    const { isConfigLoaded, isBasicAuthEnabled } = auth;
    
    // State management
    const resetForm = () => auth.resetToEmail();
    const showPassword = () => auth.resetToPassword(null, '');
    
    // Authentication logic
    const handleEmail = async (email: string) => {
      const method = await auth.determineAuthMethod(email);
      if (method.shouldUseOidc) {
        // Handle OIDC
      } else {
        // Handle basic auth
      }
    };
    
    return {
      state,
      login,
      password,
      isConfigLoaded,
      isBasicAuthEnabled,
      resetForm,
      showPassword,
      handleEmail
    };
  }
};
```

## State Management

The `AuthenticationState` interface defines the complete state:

```typescript
interface AuthenticationState {
  emailEntered: boolean;        // Has user entered email?
  showPassword: boolean;        // Show password input?
  matchedProvider: string | null; // OIDC provider if found
  providerDisplayName: string;   // Human-readable provider name
  showRetryOption: boolean;     // Show OIDC retry options?
  showErrorDetails: boolean;    // Show detailed error info?
  loading: boolean;             // General loading state
  oidcCallbackLoading: boolean; // OIDC callback loading
  password: string;             // Current password value
}
```

## Error Handling

The `parseOidcError` method categorizes OIDC errors:

```typescript
const { userMessage, isRetryable, error } = auth.parseOidcError(
  'access_denied', 
  'User cancelled authentication'
);

// userMessage: "Access denied by OIDC provider. Please try again or contact your administrator."
// isRetryable: true
// error: { error: 'access_denied', description: 'User cancelled authentication' }
```

## Testing Benefits

- **AuthenticationManager**: Can be unit tested independently
- **AuthenticationService**: Can be tested with Vue 2 testing utilities
- **useAuthentication**: Can be tested with Vue Test Utils
- **Login.vue**: Focus on UI behavior, mock authentication logic

## Migration Guide

### From Old Login.vue to New Architecture

1. **Replace direct state management** with AuthenticationService
2. **Import the service**: `import { AuthenticationService } from '@/util/AuthenticationService'`
3. **Initialize in component**: `private auth = new AuthenticationService();`
4. **Access through getters**: `get state() { return this.auth.state; }`
5. **Remove duplicate authentication logic** from the component

### For Other Components

1. Import the appropriate service: `AuthenticationService` for Vue 2, `useAuthentication` for Vue 3
2. Use in component lifecycle
3. Access state and methods through the service object
4. Remove duplicate authentication logic from the component

## Future Enhancements

- Add TypeScript strict mode support
- Add unit tests for AuthenticationManager and AuthenticationService
- Add integration tests for useAuthentication
- Consider adding authentication state persistence
- Add support for multiple authentication flows
- Add Vue 2 to Vue 3 migration helpers
