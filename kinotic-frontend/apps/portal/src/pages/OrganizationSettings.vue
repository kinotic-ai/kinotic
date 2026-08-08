<script setup lang="ts">
import GitHubLinkStatus from '@/components/GitHubLinkStatus.vue'
</script>

<template>
  <div>
    <div class="mb-2 text-sm text-surface-500">Organization</div>
    <h1 class="mb-6 text-2xl font-semibold text-surface-950 dark:text-surface-0">Organization settings</h1>

    <section class="max-w-[720px]">
      <h2 class="mb-3 text-sm font-semibold text-surface-950 dark:text-surface-0">Integrations</h2>
      <GitHubLinkStatus return-to="/organization-settings" />
    </section>
  </div>
</template>
