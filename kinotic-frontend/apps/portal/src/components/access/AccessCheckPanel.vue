<template>
  <DashboardSection :icon="SearchCheck" :tint="tint" title="Check access"
                    description="Ask whether someone holds a permission here, and which grants give it.">
    <div class="flex flex-col gap-4 p-5">
      <div class="grid gap-3 md:grid-cols-[1fr_1fr_auto]">
        <Select v-model="subjectKey" :options="subjectOptions" option-label="label" option-value="key" filter
                :filter-fields="['label', 'detail']" option-group-label="label" option-group-children="items"
                placeholder="Member or group" class="w-full">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="text-sm">{{ option.label }}</span>
              <span v-if="option.detail" class="text-xs text-muted-color">{{ option.detail }}</span>
            </div>
          </template>
        </Select>
        <Select v-model="permission" :options="permissionOptions" option-label="label" option-value="name"
                placeholder="Permission" class="w-full" />
        <Button type="button" label="Check" icon="pi pi-search" :loading="checking" :disabled="!subjectKey || !permission" @click="check" />
      </div>

      <div v-if="explanation" :class="['flex items-start gap-3 rounded-xl border p-4', explanation.allowed
          ? 'border-green-200 bg-green-50 dark:border-green-500/30 dark:bg-green-500/10'
          : 'border-red-200 bg-red-50 dark:border-red-500/30 dark:bg-red-500/10']">
        <component :is="explanation.allowed ? CircleCheck : CircleX" :size="20" :stroke-width="1.75"
                   :class="['mt-0.5 shrink-0', explanation.allowed ? 'text-green-600 dark:text-green-300' : 'text-red-600 dark:text-red-300']" aria-hidden="true" />
        <div class="min-w-0 flex-1 text-sm">
          <p class="font-medium text-surface-950 dark:text-surface-0">
            {{ checkedLabel }} {{ explanation.allowed ? 'holds' : 'does not hold' }} {{ checkedPermission }} on this {{ typeLabel(resource.type).toLowerCase() }}
          </p>
          <p v-if="explanation.allowed && explanation.through.length === 0" class="mt-1 text-muted-color">
            Held through nothing a grant explains, such as being a member of the organization.
          </p>
          <ul v-else-if="explanation.through.length > 0" class="mt-2 flex flex-col gap-1.5">
            <li v-for="grant in explanation.through" :key="grant.id" class="flex flex-wrap items-center gap-2 text-surface-800 dark:text-surface-100">
              <span>{{ roleName(grant.roleId) }}</span>
              <span class="text-muted-color">granted to {{ labelOf(grant.subject) }} on</span>
              <TableChip :icon="MapPin" :to="accessPath(grant.resource, context) ?? undefined">{{ typeLabel(grant.resource.type) }} {{ grant.resource.id }}</TableChip>
            </li>
          </ul>
          <p v-else class="mt-1 text-muted-color">No grant on this {{ typeLabel(resource.type).toLowerCase() }} or above it gives the permission.</p>
        </div>
      </div>
    </div>
  </DashboardSection>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { CircleCheck, CircleX, MapPin, SearchCheck } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { AccessExplanation, Resource, RoleDefinition, Subject } from '@kinotic-ai/management-api'
import { DashboardSection, TableChip, showErrorToast } from '@kinotic-ai/frontend-common'
import { accessPath, permissionLabel, splitPermission, typeLabel } from '@/util/access'
import type { SubjectOption } from './useSubjects'

/**
 * Explains anyone's access to the resource: whether a member or a group holds one of the resource's permissions,
 * and the grants on the resource and its ancestors it holds the permission through.
 */
const props = defineProps<{
  resource: Resource
  tint: string
  members: SubjectOption[]
  groups: SubjectOption[]
  roles: RoleDefinition[]
  /** The catalog: the model names of every permission, by type. */
  catalog: Record<string, string[]>
  labelOf: (subject: Subject) => string
  /** The ancestors the caller is looking at, for the links to where a grant was made. */
  context: { applicationId?: string, projectId?: string }
}>()

interface CheckOption extends SubjectOption {
  key: string
}

const toast = useToast()
const subjectKey = ref<string | null>(null)
const permission = ref<string | null>(null)
const checking = ref(false)
const explanation = ref<AccessExplanation | null>(null)
const checkedLabel = ref('')
const checkedPermission = ref('')

const subjectOptions = computed(() => [
  { label: 'Members', items: props.members.map(withKey) },
  { label: 'Groups', items: props.groups.map(withKey) }
])

// explain answers for a permission of the resource's own type, checked on the resource
const permissionOptions = computed(() => (props.catalog[props.resource.type] ?? []).map(name => {
  const split = splitPermission(name)
  return { name, label: permissionLabel(split.permission) }
}))

function withKey(option: SubjectOption): CheckOption {
  return { ...option, key: `${option.subject.kind}:${option.subject.id}` }
}

function roleName(roleId: string): string {
  return props.roles.find(role => role.id === roleId)?.name ?? roleId
}

async function check(): Promise<void> {
  const picked = [...props.members, ...props.groups].map(withKey).find(option => option.key === subjectKey.value)
  if (!picked || !permission.value) {
    return
  }
  const split = splitPermission(permission.value)
  checking.value = true
  try {
    explanation.value = await Kinotic.permissions.explain(picked.subject, split.permission, props.resource)
    checkedLabel.value = picked.label
    checkedPermission.value = permissionLabel(split.permission).toLowerCase()
  } catch (err) {
    showErrorToast(toast, 'Failed to check access', err, { life: 8000 })
  } finally {
    checking.value = false
  }
}
</script>
