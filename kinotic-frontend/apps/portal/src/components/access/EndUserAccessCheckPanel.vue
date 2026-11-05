<template>
  <AccessCheckPanel :tint="tint"
                    description="Ask whether a user or a machine holds a permission on the whole application, on one tenant, on one definition's rows or on those rows in one tenant, and which grants give it."
                    :subject-groups="[{ label: 'Users', items: users }, { label: 'Machines', items: machines }]"
                    subject-placeholder="User or machine" :permission-options="permissionOptions" :roles="roles" :label-of="labelOf"
                    :initial-subject="initialSubject" grid-class="md:grid-cols-[1fr_1fr_1fr_1fr_auto]"
                    held-without-grant="Held through nothing a grant explains." no-grant="No grant reaching the resource gives the permission."
                    :explain="explain">
    <template #selectors>
      <Select v-model="where" :options="whereOptions" option-label="label" option-value="id" editable placeholder="Whole application or a tenant id" class="w-full" />
      <Select v-model="definitionId" :options="definitionOptions" option-label="label" option-value="id" placeholder="Every definition or one" class="w-full" />
    </template>
    <template #grant-resource="{ grant }">
      <span class="text-muted-color">on</span>
      <TableChip :icon="MapPin">{{ resourceLabel(grant.resource, definitions) }}</TableChip>
    </template>
  </AccessCheckPanel>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Select from 'primevue/select'
import { MapPin } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type Resource, type RoleDefinition, type Subject, SubjectType } from '@kinotic-ai/management-api'
import { TableChip, permissionLabel, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import { AccessCheckPanel, type CheckedAccess, type SubjectOption } from '@kinotic-ai/frontend-common/management'
import { type DefinitionOption, definitionName, definitionOf, resourceLabel, resourceOf, tenantOf } from './applicationResources'

/**
 * Explains a user's or a machine's access in the application's own store: whether it holds a permission of one
 * of the application's definitions or services on the whole application, on a tenant, on a definition's rows in
 * every tenant or on those rows in one tenant, and the grants it holds the permission through.
 */
const props = defineProps<{
  tint: string
  applicationId: string
  users: SubjectOption[]
  machines: SubjectOption[]
  roles: RoleDefinition[]
  /** The model names of the permissions the application's store defines. */
  catalog: string[]
  /** The tenants known so far. */
  tenants: string[]
  /** The application's published definitions. */
  definitions: DefinitionOption[]
  labelOf: (subject: Subject) => string
  /** The subject to start with picked, such as the one the page was opened for. */
  initialSubjectId?: string
  /** The resource to start with, the one the page is looking at. */
  initialResource: Resource
}>()

const WHOLE_APPLICATION = ''
const EVERY_DEFINITION = ''

const initialSubject = props.initialSubjectId ? { type: SubjectType.USER, id: props.initialSubjectId } : undefined
const where = ref<string>(tenantOf(props.initialResource) ?? WHOLE_APPLICATION)
const definitionId = ref<string>(definitionOf(props.initialResource) ?? EVERY_DEFINITION)

const whereOptions = computed(() => [
  { label: 'Whole application', id: WHOLE_APPLICATION },
  ...props.tenants.map(id => ({ label: `Tenant ${id}`, id }))
])

const definitionOptions = computed(() => [
  { label: 'Every definition', id: EVERY_DEFINITION },
  ...props.definitions.map(definition => ({ label: definition.name, id: definition.id }))
])

const permissionOptions = computed(() => props.catalog.map(name => {
  const split = splitPermission(name)
  return { name, label: `${typeLabel(split.type)}: ${permissionLabel(split.permission)}` }
}))

// where the permission was asked about, as the answer reads: across the application, in a tenant, or for a
// definition's rows there or in every tenant
function whereLabel(resource: Resource): string {
  const tenant = tenantOf(resource)
  const definition = definitionOf(resource)
  let ret: string
  if (definition) {
    const name = definitionName(definition, props.definitions)
    ret = tenant ? `for ${name} rows in tenant ${tenant}` : `for ${name} rows in every tenant`
  } else {
    ret = tenant ? `in tenant ${tenant}` : 'across the application'
  }
  return ret
}

async function explain(subject: Subject, permission: string): Promise<CheckedAccess> {
  const resource = resourceOf(props.applicationId, where.value.trim(), definitionId.value)
  const explanation = await Kinotic.applicationAccess.explain(props.applicationId, subject, permission, resource)
  const split = splitPermission(permission)
  return { explanation, asked: `${permissionLabel(split.permission).toLowerCase()} on ${typeLabel(split.type).toLowerCase()} ${whereLabel(resource)}` }
}
</script>
