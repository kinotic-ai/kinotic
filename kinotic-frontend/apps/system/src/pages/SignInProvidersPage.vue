<template>
  <div class="flex flex-col">
    <PageHeader title="Sign-in providers"
                description="How system users sign in to the system console." />
    <SignInProvidersPanel owner="system" :application-id="null" />
  </div>
</template>

<script setup lang="ts">
import { PageHeader } from '@kinotic-ai/frontend-common'
import { SignInProvidersPanel } from '@kinotic-ai/frontend-common/management'
</script>
