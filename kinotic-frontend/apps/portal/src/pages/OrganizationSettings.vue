<script setup lang="ts">
import GitHubLinkStatus from '@/components/GitHubLinkStatus.vue'
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="Organization settings" description="Settings that apply to everyone in your organization." />

    <section class="max-w-[720px]">
      <h2 class="mb-3 text-sm font-semibold text-surface-950 dark:text-surface-0">Integrations</h2>
      <GitHubLinkStatus return-to="/organization-settings" />
    </section>
  </div>
</template>
