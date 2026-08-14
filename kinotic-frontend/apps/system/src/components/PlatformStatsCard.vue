<template>
  <Card>
    <template #title>Platform</template>
    <template #content>
      <div v-if="error">
        <Message severity="error" :closable="false">{{ error }}</Message>
      </div>
      <div v-else class="flex gap-8">
        <div class="flex flex-col gap-1">
          <span class="text-2xl font-semibold">{{ organizationCount ?? '—' }}</span>
          <span class="text-xs text-muted-color">Organizations</span>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Card from 'primevue/card'
import Message from 'primevue/message'

import { Kinotic } from '@kinotic-ai/core'

const organizationCount = ref<number | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    organizationCount.value = await Kinotic.systemOrganizations.countOrganizations()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load platform stats'
  }
})
</script>
