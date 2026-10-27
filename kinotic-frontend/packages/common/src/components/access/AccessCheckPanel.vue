<template>
  <DashboardSection :icon="SearchCheck" :tint="tint" title="Check access" :description="description">
    <div class="flex flex-col gap-4 p-5">
      <div :class="['grid gap-3', gridClass]">
        <Select v-model="subjectKey" :options="groupedOptions" option-label="label" option-value="key" filter
                :filter-fields="['label', 'detail']" option-group-label="label" option-group-children="items"
                :placeholder="subjectPlaceholder" class="w-full">
          <template #option="{ option }">
            <SubjectOptionItem :option="option" />
          </template>
        </Select>
        <slot name="selectors" />
        <Select v-model="permission" :options="permissionOptions" option-label="label" option-value="name"
                placeholder="Permission" class="w-full" />
        <Button type="button" label="Check" icon="pi pi-search" :loading="checking" :disabled="!subjectKey || !permission" @click="check" />
      </div>

      <div v-if="checked" :class="['flex items-start gap-3 rounded-xl border p-4', checked.explanation.allowed
          ? 'border-green-200 bg-green-50 dark:border-green-500/30 dark:bg-green-500/10'
          : 'border-red-200 bg-red-50 dark:border-red-500/30 dark:bg-red-500/10']">
        <component :is="checked.explanation.allowed ? CircleCheck : CircleX" :size="20" :stroke-width="1.75"
                   :class="['mt-0.5 shrink-0', checked.explanation.allowed ? 'text-green-600 dark:text-green-300' : 'text-red-600 dark:text-red-300']" aria-hidden="true" />
        <div class="min-w-0 flex-1 text-sm">
          <p class="font-medium text-surface-950 dark:text-surface-0">
            {{ checkedLabel }} {{ checked.explanation.allowed ? 'holds' : 'does not hold' }} {{ checked.asked }}
          </p>
          <p v-if="checked.explanation.allowed && checked.explanation.through.length === 0" class="mt-1 text-muted-color">
            {{ heldWithoutGrant }}
          </p>
          <ul v-else-if="checked.explanation.through.length > 0" class="mt-2 flex flex-col gap-1.5">
            <li v-for="grant in checked.explanation.through" :key="grant.id" class="flex flex-wrap items-center gap-2 text-surface-800 dark:text-surface-100">
              <span>{{ roleName(grant.roleId) }}</span>
              <span class="text-muted-color">granted to {{ labelOf(grant.subject) }}</span>
              <slot name="grant-resource" :grant="grant" />
            </li>
          </ul>
          <p v-else class="mt-1 text-muted-color">{{ noGrant }}</p>
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
import { CircleCheck, CircleX, SearchCheck } from '@lucide/vue'
import type { RoleDefinition, Subject } from '@kinotic-ai/management-api'
import DashboardSection from '../DashboardSection.vue'
import { showErrorToast } from '../../util/helpers'
import type { CheckedAccess } from './CheckedAccess'
import type { SubjectOption } from './SubjectOption'
import SubjectOptionItem from './SubjectOptionItem.vue'

/**
 * Explains a subject's access: whether a subject picked from the groups holds a permission, through the given
 * explain call, and the grants it holds the permission through. The selectors slot sits between the subject and
 * the permission, for pickers of where the check is made; the grant-resource slot follows each grant.
 */
const props = withDefaults(defineProps<{
  tint: string
  description: string
  /** The subjects offered, in labelled groups. */
  subjectGroups: { label: string, items: SubjectOption[] }[]
  subjectPlaceholder: string
  /** The permissions offered: each one's model name and its label. */
  permissionOptions: { name: string, label: string }[]
  roles: RoleDefinition[]
  labelOf: (subject: Subject) => string
  /** The subject to start with picked, such as the one the page was opened for. */
  initialSubject?: Subject
  /** The columns of the pickers' row at medium width and up. */
  gridClass?: string
  /** What an allowed answer through no grant says. */
  heldWithoutGrant: string
  /** What a refused answer says. */
  noGrant: string
  explain: (subject: Subject, permission: string) => Promise<CheckedAccess>
}>(), {
  initialSubject: undefined,
  gridClass: 'md:grid-cols-[1fr_1fr_auto]'
})

const toast = useToast()
const subjectKey = ref<string | null>(props.initialSubject ? keyOf(props.initialSubject) : null)
const permission = ref<string | null>(null)
const checking = ref(false)
const checked = ref<CheckedAccess | null>(null)
const checkedLabel = ref('')

// a user and a group may share an id, so a subject is picked by its type and id
const groupedOptions = computed(() => props.subjectGroups.map(group => ({
  label: group.label,
  items: group.items.map(option => ({ ...option, key: keyOf(option.subject) }))
})))

function keyOf(subject: Subject): string {
  return `${subject.type}:${subject.id}`
}

function roleName(roleId: string): string {
  return props.roles.find(role => role.id === roleId)?.name ?? roleId
}

async function check(): Promise<void> {
  const picked = props.subjectGroups.flatMap(group => group.items).find(option => keyOf(option.subject) === subjectKey.value)
  if (!picked || !permission.value) {
    return
  }
  checking.value = true
  try {
    checked.value = await props.explain(picked.subject, permission.value)
    checkedLabel.value = picked.label
  } catch (err) {
    showErrorToast(toast, 'Failed to check access', err, { life: 8000 })
  } finally {
    checking.value = false
  }
}
</script>
