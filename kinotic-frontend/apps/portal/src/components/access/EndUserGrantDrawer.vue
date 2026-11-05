<template>
  <GrantAccessDrawer :visible="visible"
                     description="Give one of this application's users or machines a role on the whole application, on one tenant, on one definition's rows, or on those rows in one tenant."
                     :kinds="kinds" :roles="roles" :ready="resource !== null" :grant="grant"
                     @update:visible="emit('update:visible', $event)" @granted="emit('granted', $event)">
    <div>
      <span class="mb-2 block text-sm font-medium">Where</span>
      <SelectButton v-model="scope" :options="SCOPES" :allow-empty="false" class="mb-3" />
      <Select v-if="scope === 'One tenant'" v-model="tenantId" :options="tenants" editable placeholder="Tenant id" class="w-full"
              empty-message="No tenant known yet; type its id" />
    </div>
    <div>
      <span class="mb-2 block text-sm font-medium">Which rows</span>
      <SelectButton v-model="rows" :options="ROWS" :allow-empty="false" class="mb-3" />
      <Select v-if="rows === 'One definition'" v-model="definitionId" :options="definitions" option-label="name" option-value="id" filter
              placeholder="Choose a definition" class="w-full" empty-message="No published definition yet" />
      <p class="mt-1.5 text-[0.8125rem] text-muted-color">{{ reach }}</p>
    </div>
  </GrantAccessDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import { Kinotic } from '@kinotic-ai/core'
import type { Grant, Resource, RoleDefinition, Subject } from '@kinotic-ai/management-api'
import { GrantAccessDrawer, type SubjectKind, type SubjectOption } from '@kinotic-ai/frontend-common/management'
import { type DefinitionOption, definitionOf, resourceOf, tenantOf } from './applicationResources'

/**
 * Grants a role in the application's own store to one of its users or machines, on the application, on one
 * tenant, on one definition's rows in every tenant, or on those rows in one tenant: the subject holds every
 * permission the role bundles on the rows there.
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
  /** The application's published definitions. */
  definitions: DefinitionOption[]
  /** Where the form starts: the resource the page is looking at. */
  initialResource: Resource
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'granted', grant: Grant): void
}>()

const SCOPES = ['Whole application', 'One tenant']
const ROWS = ['Every definition', 'One definition']

const scope = ref<'Whole application' | 'One tenant'>('Whole application')
const rows = ref<'Every definition' | 'One definition'>('Every definition')
const tenantId = ref<string | null>(null)
const definitionId = ref<string | null>(null)

const kinds = computed<SubjectKind[]>(() => [
  { label: 'User', options: props.users, placeholder: 'Choose a user', emptyMessage: 'No users yet' },
  { label: 'Machine', options: props.machines, placeholder: 'Choose a machine', emptyMessage: 'No machines yet' }
])

/** The resource the grant is made on, or null while a tenant or a definition chosen is not named yet. */
const resource = computed<Resource | null>(() => {
  const tenant = scope.value === 'One tenant' ? tenantId.value?.trim() ?? '' : ''
  const definition = rows.value === 'One definition' ? definitionId.value ?? '' : ''
  const named = (scope.value !== 'One tenant' || tenant.length > 0) && (rows.value !== 'One definition' || definition.length > 0)
  return named ? resourceOf(props.applicationId, tenant, definition) : null
})

const reach = computed(() => {
  let ret: string
  if (scope.value === 'One tenant') {
    ret = rows.value === 'One definition' ? 'The role reaches those rows in that tenant alone.' : 'The role reaches the rows of every definition in that tenant.'
  } else {
    ret = rows.value === 'One definition'
        ? 'The role reaches those rows in every tenant, including ones created later.'
        : 'The role reaches the rows of every definition in every tenant, including ones created later.'
  }
  return ret
})

watch(() => props.visible, visible => {
  if (visible) {
    const tenant = tenantOf(props.initialResource)
    const definition = definitionOf(props.initialResource)
    scope.value = tenant ? 'One tenant' : 'Whole application'
    tenantId.value = tenant
    rows.value = definition ? 'One definition' : 'Every definition'
    definitionId.value = definition
  }
})

// the drawer enables Grant only once the resource is named
function grant(subject: Subject, roleId: string): Promise<Grant> {
  return Kinotic.applicationAccess.grant(props.applicationId, subject, roleId, resource.value as Resource)
}
</script>
