<template>
  <span :class="['hidden items-center rounded-full border px-2 py-px text-[10px] font-semibold uppercase leading-4 tracking-[0.08em] sm:inline-flex', COLORS[kind]]">{{ kind }}</span>
</template>

<script setup lang="ts">
/** The kind of scope a breadcrumb segment names. */
export type ScopeKind = 'system' | 'organization' | 'application' | 'project'

/**
 * The small outlined pill after a breadcrumb segment's name, naming the kind of scope it is in
 * that kind's color. Hidden on the narrowest screens.
 */
defineProps<{
  kind: ScopeKind
}>()

const COLORS: Record<ScopeKind, string> = {
  system: 'border-red-200 bg-red-50 text-red-600 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300',
  organization: 'border-green-200 bg-green-50 text-green-700 dark:border-green-500/30 dark:bg-green-500/10 dark:text-green-300',
  application: 'border-blue-200 bg-blue-50 text-blue-600 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-300',
  project: 'border-purple-200 bg-purple-50 text-purple-600 dark:border-purple-500/30 dark:bg-purple-500/10 dark:text-purple-300'
}
</script>
