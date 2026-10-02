<template>
  <div class="flex flex-col items-center justify-center text-center">
    <svg width="104" height="84" viewBox="0 0 104 84" fill="none" aria-hidden="true" class="empty-chart">
      <!-- the empty chart it stands in: a dashed baseline and two hollow bars -->
      <path d="M8 74h88" class="stroke-surface-300 dark:stroke-surface-600" stroke-width="1.5" stroke-dasharray="3 4" stroke-linecap="round" />
      <rect x="16" y="60" width="12" height="14" rx="3" class="stroke-surface-300 dark:stroke-surface-600" stroke-width="1.5" stroke-dasharray="3 3" />
      <rect x="76" y="54" width="12" height="20" rx="3" class="stroke-surface-300 dark:stroke-surface-600" stroke-width="1.5" stroke-dasharray="3 3" />

      <ellipse cx="52" cy="72" rx="15" ry="2.5" class="fill-surface-200 dark:fill-surface-700" />
      <g class="empty-chart__body">
        <!-- the character: a relaxed little square on stick legs, waiting for its data -->
        <path d="M46 64v7h-2.5M58 64v7h2.5" class="stroke-surface-500 dark:stroke-surface-400" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        <rect x="36" y="30" width="32" height="34" rx="7" class="fill-surface-0 stroke-green-500 dark:fill-surface-800 dark:stroke-green-400" stroke-width="1.75" />
        <g class="empty-chart__eyes">
          <ellipse cx="46" cy="44" rx="2.3" ry="1.6" class="fill-surface-700 dark:fill-surface-200" />
          <ellipse cx="58" cy="44" rx="2.3" ry="1.6" class="fill-surface-700 dark:fill-surface-200" />
        </g>
        <path d="M48 52h8" class="stroke-surface-700 dark:stroke-surface-200" stroke-width="1.5" stroke-linecap="round" />
      </g>
    </svg>
    <p class="mt-3 text-sm font-medium text-surface-800 dark:text-surface-100">{{ title }}</p>
    <p v-if="hint" class="mt-0.5 text-xs text-muted-color">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
/**
 * A small character standing in an empty chart, for a chart or list with nothing to show: it
 * blinks, bobs, and waits for the data. The title says what is missing, the optional hint what
 * might bring it.
 */
defineProps<{
  title: string
  hint?: string
}>()
</script>

<style scoped>
.empty-chart__body {
  animation: empty-chart-bob 3.2s ease-in-out infinite;
}

.empty-chart__eyes {
  transform-box: fill-box;
  transform-origin: center;
  animation: empty-chart-blink 4.5s ease-in-out infinite;
}

@keyframes empty-chart-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

/* a quick blink near the end of each cycle, the eyes open the rest of the time */
@keyframes empty-chart-blink {
  0%, 92%, 100% { transform: scaleY(1); }
  95% { transform: scaleY(0.1); }
}

@media (prefers-reduced-motion: reduce) {
  .empty-chart__body,
  .empty-chart__eyes {
    animation: none;
  }
}
</style>
