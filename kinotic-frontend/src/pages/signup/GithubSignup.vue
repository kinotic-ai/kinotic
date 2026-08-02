<template>
  <AuthPageShell>
    <div class="login-form">
      <h2 class="signup-title">Create your organization</h2>

      <p class="signup-subtitle">
        Kinotic projects are backed by GitHub repositories, so your organization starts
        with your GitHub identity.
      </p>

      <div class="signup-providers">
        <SocialAuthButton
          provider="github"
          :action="apiUrl('/api/auth/org/signup/social/start/github')"
          intent="sign-up"
        />
      </div>

      <div class="signup-footer-link">
        Already have an account? <router-link to="/login" class="login-link">Sign in</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { apiUrl } from '@/util/helpers'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
</script>

<style scoped>
.signup-subtitle {
  margin: 0 0 1rem;
  max-width: 22rem;
  text-align: center;
  line-height: 1.5;
  color: var(--p-text-muted-color);
  font-size: 0.9375rem;
}

.signup-providers {
  width: min(100%, 20rem);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0.5rem 0 1.5rem;
}

.signup-footer-link {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.875rem;
}
</style>
