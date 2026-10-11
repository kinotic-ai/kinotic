<template>
  <span class="flex flex-col">
    <span class="flex items-center gap-2 text-sm text-surface-950 dark:text-surface-0">
      {{ role?.name ?? roleId }}
      <Tag v-if="role?.builtIn" value="Built-in" severity="secondary" class="!text-[10px]" />
    </span>
    <span v-if="role" class="text-xs text-muted-color" v-tooltip.top="permissionsTooltip">{{ role.permissions.length }} permission{{ role.permissions.length === 1 ? '' : 's' }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { permissionLabel, splitPermission, typeLabel } from '../util/access'

/** A grant's role in a table row: its name, whether built-in, and its permissions on hover. */
const props = defineProps<{
  role: RoleDefinition | undefined
  roleId: string
}>()

const permissionsTooltip = computed(() => props.role?.permissions
    .map(name => { const split = splitPermission(name); return `${typeLabel(split.type)}: ${permissionLabel(split.permission)}` })
    .join('\n'))
</script>
