<template>
  <FormDialog :visible="visible" :icon="UserPlus" title="Grant access" :description="description"
              @update:visible="emit('update:visible', $event)" @submit="submit">
    <div class="flex flex-col gap-5">
      <div>
        <span class="mb-2 block text-sm font-medium">Who</span>
        <SelectButton v-model="kind" :options="KINDS" option-label="label" option-value="value" :allow-empty="false" class="mb-3" />
        <Select v-model="subjectId" :options="kind === SubjectKind.USER ? members : groups" option-label="label" option-value="subject.id"
                filter :filter-fields="['label', 'detail']" :placeholder="kind === SubjectKind.USER ? 'Choose a member' : 'Choose a group'"
                class="w-full" :empty-message="kind === SubjectKind.USER ? 'No members' : 'No groups yet'">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="text-sm">{{ option.label }}</span>
              <span v-if="option.detail" class="text-xs text-muted-color">{{ option.detail }}</span>
            </div>
          </template>
        </Select>
      </div>
      <div>
        <label for="grant-role" class="mb-2 block text-sm font-medium">Role</label>
        <Select id="grant-role" v-model="roleId" :options="roles" option-label="name" option-value="id" filter
                placeholder="Choose a role" class="w-full">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="flex items-center gap-2 text-sm">
                {{ option.name }}
                <Tag v-if="option.builtIn" value="Built-in" severity="secondary" class="!text-[10px]" />
              </span>
              <span class="text-xs text-muted-color">{{ summarize(option) }}</span>
            </div>
          </template>
        </Select>
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">The role's permissions reach this {{ typeLabel(resource.type).toLowerCase() }} and everything inside it.</p>
      </div>
    </div>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" label="Grant" icon="pi pi-check" :loading="granting" :disabled="!subjectId || !roleId" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { UserPlus } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type Grant, type Resource, type RoleDefinition, SubjectKind } from '@kinotic-ai/management-api'
import { FormDialog, permissionLabel, showErrorToast, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import type { SubjectOption } from './useSubjects'

/**
 * Grants a role to a member or a group on the resource: the subject holds every permission the role bundles on
 * the resource and on everything inside it. Only the roles whose permissions reach the resource are offered.
 */
const props = defineProps<{
  visible: boolean
  resource: Resource
  /** The roles that fit the resource. */
  roles: RoleDefinition[]
  members: SubjectOption[]
  groups: SubjectOption[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'granted', grant: Grant): void
}>()

const KINDS = [
  { label: 'Member', value: SubjectKind.USER },
  { label: 'Group', value: SubjectKind.GROUP }
]

const toast = useToast()
const kind = ref<SubjectKind>(SubjectKind.USER)
const subjectId = ref<string | null>(null)
const roleId = ref<string | null>(null)
const granting = ref(false)

const description = computed(() => `Give a member or a group a role on this ${typeLabel(props.resource.type).toLowerCase()}.`)

watch(() => props.visible, visible => {
  if (visible) {
    kind.value = SubjectKind.USER
    subjectId.value = null
    roleId.value = null
  }
})

// a picked member is not a group, so switching the kind clears the pick
watch(kind, () => { subjectId.value = null })

function summarize(role: RoleDefinition): string {
  const labels = role.permissions.map(name => {
    const split = splitPermission(name)
    return `${typeLabel(split.type)}: ${permissionLabel(split.permission).toLowerCase()}`
  })
  return labels.length <= 3 ? labels.join(', ') : `${labels.slice(0, 3).join(', ')} and ${labels.length - 3} more`
}

async function submit(): Promise<void> {
  if (!subjectId.value || !roleId.value) {
    return
  }
  granting.value = true
  try {
    const grant = await Kinotic.permissions.grant({ kind: kind.value, id: subjectId.value }, roleId.value, props.resource)
    toast.add({ severity: 'success', summary: 'Access granted', life: 4000 })
    emit('granted', grant)
    emit('update:visible', false)
  } catch (err) {
    showErrorToast(toast, 'Failed to grant access', err, { life: 8000 })
  } finally {
    granting.value = false
  }
}
</script>
