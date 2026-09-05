<template>
  <AuthPageShell>
    <div class="login-form">
      <h2 class="signup-title">Create your organization</h2>

      <p class="mb-4 max-w-88 text-center leading-normal text-muted-color text-[0.9375rem]">
        Kinotic projects are backed by GitHub repositories, so your organization starts
        with your GitHub identity.
      </p>

      <div class="w-full max-w-80 flex flex-col gap-3 mt-2 mb-6">
        <SocialAuthButton
          provider="github"
          :action="apiUrl('/api/auth/org/signup/social/start/github')"
          intent="sign-up"
        />
      </div>

      <div class="text-center mt-4 text-sm">
        Already have an account? <router-link to="/login" class="login-link">Sign in</router-link>
      </div>
    </div>
  </AuthPageShell>
</template>

<script setup lang="ts">
import { apiUrl } from '@kinotic-ai/frontend-common'
import { AuthPageShell } from '@kinotic-ai/frontend-common'
import SocialAuthButton from '@/components/SocialAuthButton.vue'
</script>
