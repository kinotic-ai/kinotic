<template>
  <FormDialog :visible="visible" :icon="UserPlus" title="Grant access"
              description="Give one of this application's users or machines a role on the whole application or on one tenant."
              @update:visible="emit('update:visible', $event)" @submit="submit">
    <div class="flex flex-col gap-5">
      <div>
        <span class="mb-2 block text-sm font-medium">Who</span>
        <SelectButton v-model="kind" :options="KINDS" :allow-empty="false" class="mb-3" />
        <Select v-model="subjectId" :options="kind === 'User' ? users : machines" option-label="label" option-value="subject.id"
                filter :filter-fields="['label', 'detail']" :placeholder="kind === 'User' ? 'Choose a user' : 'Choose a machine'"
                class="w-full" :empty-message="kind === 'User' ? 'No users yet' : 'No machines yet'">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="text-sm">{{ option.label }}</span>
              <span v-if="option.detail" class="text-xs text-muted-color">{{ option.detail }}</span>
            </div>
          </template>
        </Select>
      </div>
      <div>
        <span class="mb-2 block text-sm font-medium">Where</span>
        <SelectButton v-model="scope" :options="SCOPES" :allow-empty="false" class="mb-3" />
        <Select v-if="scope === 'One tenant'" v-model="tenantId" :options="tenants" editable placeholder="Tenant id" class="w-full"
                empty-message="No tenant known yet; type its id" />
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">
          {{ scope === 'One tenant' ? 'The role reaches the rows of that tenant alone.' : 'The role reaches the rows of every tenant, including ones created later.' }}
        </p>
      </div>
      <div>
        <label for="end-user-grant-role" class="mb-2 block text-sm font-medium">Role</label>
        <Select id="end-user-grant-role" v-model="roleId" :options="roles" option-label="name" option-value="id" filter
                placeholder="Choose a role" class="w-full">
          <template #option="{ option }">
            <div class="flex flex-col">
              <span class="text-sm">{{ option.name }}</span>
              <span class="text-xs text-muted-color">{{ summarize(option) }}</span>
            </div>
          </template>
        </Select>
      </div>
    </div>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" label="Grant" icon="pi pi-check" :loading="granting" :disabled="!subjectId || !roleId || !resource" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import { useToast } from 'primevue/usetoast'
import { UserPlus } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type Grant, type Resource, type RoleDefinition, SubjectKind } from '@kinotic-ai/management-api'
import { FormDialog, permissionLabel, showErrorToast, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import type { SubjectOption } from './useSubjects'

/**
 * Grants a role in the application's own store to one of its users or machines, on the application, which
 * reaches every tenant, or on one tenant: the subject holds every permission the role bundles on the rows there.
 */
const props = defineProps<{
  visible: boolean
  applicationId: string
  /** The roles the application's store defines. */
  roles: RoleDefinition[]
  users: SubjectOption[]
  machines: SubjectOption[]
  /** The tenants known so far, offered in the picker; any other id can be typed. */
  tenants: string[]
  /** Where the dialog starts: the resource the page is looking at. */
  initialResource: Resource
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'granted', grant: Grant): void
}>()

const KINDS = ['User', 'Machine']
const SCOPES = ['Whole application', 'One tenant']

const toast = useToast()
const kind = ref<'User' | 'Machine'>('User')
const scope = ref<'Whole application' | 'One tenant'>('Whole application')
const subjectId = ref<string | null>(null)
const tenantId = ref<string | null>(null)
const roleId = ref<string | null>(null)
const granting = ref(false)

const resource = computed<Resource | null>(() => {
  let ret: Resource | null
  if (scope.value === 'Whole application') {
    ret = { type: 'application', id: props.applicationId }
  } else {
    const id = tenantId.value?.trim() ?? ''
    ret = id.length > 0 ? { type: 'tenant', id } : null
  }
  return ret
})

watch(() => props.visible, visible => {
  if (visible) {
    kind.value = 'User'
    subjectId.value = null
    roleId.value = null
    scope.value = props.initialResource.type === 'tenant' ? 'One tenant' : 'Whole application'
    tenantId.value = props.initialResource.type === 'tenant' ? props.initialResource.id : null
  }
})

// a picked user is not a machine, so switching the kind clears the pick
watch(kind, () => { subjectId.value = null })

function summarize(role: RoleDefinition): string {
  const labels = role.permissions.map(name => {
    const split = splitPermission(name)
    return `${typeLabel(split.type)}: ${permissionLabel(split.permission).toLowerCase()}`
  })
  return labels.length <= 3 ? labels.join(', ') : `${labels.slice(0, 3).join(', ')} and ${labels.length - 3} more`
}

async function submit(): Promise<void> {
  if (!subjectId.value || !roleId.value || !resource.value) {
    return
  }
  granting.value = true
  try {
    const grant = await Kinotic.applicationAccess.grant(props.applicationId, { kind: SubjectKind.USER, id: subjectId.value }, roleId.value, resource.value)
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
