<template>
  <span class="flex items-center gap-3">
    <InitialsTile :name="label" :index="index" />
    <span class="flex min-w-0 flex-col">
      <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ label }}</span>
      <span class="text-xs text-muted-color">{{ subject.kind === SubjectKind.GROUP ? 'Group' : 'Member' }}</span>
    </span>
  </span>
</template>

<script setup lang="ts">
import { type Subject, SubjectKind } from '@kinotic-ai/management-api'
import { InitialsTile } from '@kinotic-ai/frontend-common'

/** A grant's subject in a table row: who they are, and whether a member or a group. */
defineProps<{
  subject: Subject
  label: string
  /** The row's position, which picks the tile's colour. */
  index: number
}>()
</script>
