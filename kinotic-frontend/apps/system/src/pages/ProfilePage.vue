<template>
  <div class="max-w-[860px]">
    <PageHeader title="Profile" description="How you sign in to the system console." />
    <SignInMethodsSection description="How you sign in to this console." connect-path="/api/auth/system/methods/start/"
                          providers-path="/api/auth/system/login/providers" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'
import { SignInMethodsSection } from '@kinotic-ai/frontend-common/management'
</script>
