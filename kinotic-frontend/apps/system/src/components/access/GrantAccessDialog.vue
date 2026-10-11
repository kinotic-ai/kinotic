<template>
  <FormDialog :visible="visible" :icon="UserPlus" title="Grant access"
              description="Give an operator or a machine a role on the platform, which reaches every organization and worker node on it."
              @update:visible="emit('update:visible', $event)" @submit="submit">
    <div class="flex flex-col gap-5">
      <div>
        <span class="mb-2 block text-sm font-medium">Who</span>
        <SelectButton v-model="staff" :options="STAFF" :allow-empty="false" class="mb-3" />
        <Select v-model="subjectId" :options="staff === 'Operator' ? operators : machines" option-label="label" option-value="subject.id"
                filter :filter-fields="['label', 'detail']" :placeholder="staff === 'Operator' ? 'Choose an operator' : 'Choose a machine'"
                class="w-full" :empty-message="staff === 'Operator' ? 'No operators' : 'No machines yet'">
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
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">The role's permissions reach the platform and everything on it: a registrar registers nodes, support reads everything, an administrator does everything.</p>
      </div>
    </div>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" label="Grant" icon="pi pi-check" :loading="granting" :disabled="!subjectId || !roleId" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { UserPlus } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type Grant, type RoleDefinition, SubjectKind } from '@kinotic-ai/management-api'
import { FormDialog, permissionLabel, showErrorToast, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import type { SubjectOption } from './useSubjects'

/**
 * Grants a role to an operator or a machine on the platform: the subject holds every permission the role bundles
 * on the platform and on everything on it.
 */
const props = defineProps<{
  visible: boolean
  roles: RoleDefinition[]
  operators: SubjectOption[]
  machines: SubjectOption[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'granted', grant: Grant): void
}>()

const STAFF = ['Operator', 'Machine']

const toast = useToast()
const staff = ref<'Operator' | 'Machine'>('Operator')
const subjectId = ref<string | null>(null)
const roleId = ref<string | null>(null)
const granting = ref(false)

watch(() => props.visible, visible => {
  if (visible) {
    staff.value = 'Operator'
    subjectId.value = null
    roleId.value = null
  }
})

// a picked operator is not a machine, so switching the kind clears the pick
watch(staff, () => { subjectId.value = null })

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
    const grant = await Kinotic.systemAccess.grant({ kind: SubjectKind.USER, id: subjectId.value }, roleId.value)
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
