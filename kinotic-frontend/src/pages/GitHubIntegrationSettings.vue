<template>
  <div class="p-4">
    <h1 class="text-xl font-semibold mb-4">GitHub</h1>
    <GitHubLinkStatus return-to="/integrations/github" />
  </div>
</template>

<script setup lang="ts">
import GitHubLinkStatus from '@/components/GitHubLinkStatus.vue'
</script>
