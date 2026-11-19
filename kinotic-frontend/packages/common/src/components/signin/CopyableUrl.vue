<template>
  <div>
    <span class="mb-1.5 block text-sm font-medium text-surface-950 dark:text-surface-0">{{ label }}</span>
    <div class="flex items-center gap-2 rounded-lg border border-surface-200 bg-surface-50 py-1.5 pl-3 pr-1.5 dark:border-surface-700 dark:bg-surface-800/60">
      <Link2 :size="14" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
      <span v-if="url" class="min-w-0 flex-1 truncate font-mono text-[0.8125rem] text-surface-800 dark:text-surface-100" v-tooltip.top="url">{{ url }}</span>
      <span v-else class="min-w-0 flex-1 text-[0.8125rem] text-muted-color">Loading…</span>
      <Button type="button" :icon="copied ? 'pi pi-check' : 'pi pi-copy'" severity="secondary" text size="small" :disabled="!url"
              :aria-label="copied ? 'Copied' : `Copy ${label.toLowerCase()}`" v-tooltip.top="copied ? 'Copied' : 'Copy'" @click="copy" />
    </div>
    <p v-if="help" class="mt-1.5 text-[0.8125rem] text-muted-color">{{ help }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import { Link2 } from '@lucide/vue'

/** A URL shown read-only under its label, with a button that copies it; null while it is still loading. */
const props = defineProps<{
  label: string
  url: string | null
  help?: string
}>()

const copied = ref(false)

async function copy(): Promise<void> {
  await navigator.clipboard.writeText(props.url ?? '')
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
</script>
