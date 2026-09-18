<script setup lang="ts">
/** One headline number of the organization, in the accent that names it. */
defineProps<{
    label: string
    value: number | null
    accent: 'cyan' | 'magenta' | 'lime'
}>()
</script>

<template>
  <div class="panel tile" :class="`tile--${accent}`">
    <span class="label">{{ label }}</span>
    <span class="tile__value display" :class="`neon-${accent}`">{{ value ?? '—' }}</span>
  </div>
</template>

<style scoped>
.tile {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top-width: 2px;
}

.tile--cyan { border-top-color: var(--cyan); }
.tile--magenta { border-top-color: var(--magenta); }
.tile--lime { border-top-color: var(--lime); }

.tile__value {
  font-size: 40px;
  line-height: 1;
}
</style>
