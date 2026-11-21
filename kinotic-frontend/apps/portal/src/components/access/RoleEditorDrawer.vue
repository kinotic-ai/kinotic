<template>
  <FormDrawer :visible="visible" :title="role?.id ? 'Edit role' : 'New role'"
              :description="applicationId
                  ? 'A role says what its holders may do with each entity and each service. It is granted on a tenant or on the whole application.'
                  : 'A role bundles permissions; a grant of it on a resource gives them there and on everything inside it.'"
              @close="emit('update:visible', false)">
    <form id="role-editor-form" @submit.prevent="submit">
      <div class="flex flex-col gap-5">
        <div>
          <label for="role-name" class="mb-2 block text-sm font-medium">Name</label>
          <InputText id="role-name" v-model="name" :placeholder="applicationId ? 'Accountant' : 'Release manager'" autocomplete="off" autofocus class="w-full" />
        </div>
        <div>
          <label for="role-description" class="mb-2 flex items-center justify-between text-sm font-medium">
            Description
            <span class="text-xs font-normal text-muted-color">Optional</span>
          </label>
          <Textarea id="role-description" v-model="description" rows="2" auto-resize placeholder="What the role is for" class="w-full" />
        </div>
        <div v-if="applicationId">
          <label class="flex items-center gap-2 text-sm font-medium">
            <Checkbox v-model="tenantAssignable" binary input-id="role-tenant-assignable" />
            Tenant administrators can grant this role
          </label>
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">A customer's administrator grants it to the users of their own tenant, from your application's UI. Leave it off for a role only your organization grants.</p>
        </div>
        <div v-if="!applicationId">
          <label for="role-level" class="mb-2 block text-sm font-medium">Level</label>
          <Select id="role-level" v-model="home" :options="homes" option-label="label" option-value="type" :disabled="!!role?.id" class="w-full" />
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">The role bundles permissions of this level and below, and is granted here or above. It cannot be changed once created.</p>
        </div>
        <div v-if="rowOptions.length > 0">
          <span class="mb-2 block text-sm font-medium">Entities</span>
          <div class="rounded-xl border border-surface-200 dark:border-surface-700">
            <fieldset class="px-4 py-3">
              <legend class="float-left mb-2 w-full text-xs font-semibold uppercase tracking-wider text-surface-500 dark:text-surface-400">Every entity</legend>
              <div class="grid gap-2 sm:grid-cols-3">
                <label v-for="option in rowOptions" :key="option.name" class="flex cursor-pointer items-center gap-2 text-sm text-surface-800 dark:text-surface-100">
                  <Checkbox v-model="selected" :value="option.name" />
                  {{ option.label }}
                </label>
              </div>
            </fieldset>
            <fieldset v-for="definition in namedDefinitions" :key="definition.id" class="border-t border-surface-100 px-4 py-3 dark:border-surface-800">
              <legend class="float-left mb-2 w-full text-xs font-semibold uppercase tracking-wider text-surface-500 dark:text-surface-400">{{ definition.name }}</legend>
              <div class="grid gap-2 sm:grid-cols-3">
                <label v-for="option in rowOptions" :key="option.name" class="flex cursor-pointer items-center gap-2 text-sm text-surface-800 dark:text-surface-100">
                  <Checkbox binary :model-value="holdsOn(definition.id, option.name)" :disabled="selected.includes(option.name)"
                            @update:model-value="setOn(definition.id, option.name, $event)" />
                  {{ option.label }}
                </label>
              </div>
            </fieldset>
          </div>
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">Reading, editing and deleting an entity's rows are each granted on their own. An entity no role names is reached by your microservices alone.</p>
        </div>
        <div v-if="groups.length > 0">
          <span class="mb-2 block text-sm font-medium">{{ applicationId ? 'Services and tenant' : 'Permissions' }}</span>
          <div class="rounded-xl border border-surface-200 dark:border-surface-700">
            <fieldset v-for="group in groups" :key="group.type" class="border-t border-surface-100 px-4 py-3 first:border-t-0 dark:border-surface-800">
              <legend class="float-left mb-2 w-full text-xs font-semibold uppercase tracking-wider text-surface-500 dark:text-surface-400">{{ group.label }}</legend>
              <div class="grid gap-2 sm:grid-cols-2">
                <label v-for="option in group.options" :key="option.name" class="flex cursor-pointer items-center gap-2 text-sm text-surface-800 dark:text-surface-100">
                  <Checkbox v-model="selected" :value="option.name" />
                  {{ option.label }}
                </label>
              </div>
            </fieldset>
          </div>
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">{{ selected.length }} selected. Deleting implies editing and editing implies reading.</p>
        </div>
      </div>
    </form>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" form="role-editor-form" :label="role?.id ? 'Save role' : 'Create role'" icon="pi pi-check" :loading="saving"
              :disabled="name.trim() === '' || bundlesNothing" />
    </template>
  </FormDrawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { FormDrawer, permissionLabel, showErrorToast, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import { homeTypes, typesWithin } from '@/util/access'
import { type DefinitionOption, definitionName } from './applicationResources'
import { roleSourceOf } from './roleSource'

/**
 * Defines a custom role from the catalog, or reshapes one: its name, description and the permissions it
 * bundles, grouped by the resource type each is about. A role of an application also says, entity by entity,
 * what its holders may do with that entity's rows.
 */
const props = defineProps<{
  visible: boolean
  /** The role to edit, or null for a new one. */
  role: RoleDefinition | null
  /** The catalog: the model names of every permission, by type. */
  catalog: Record<string, string[]>
  /** The roles listed beside it, whose built-in ones give the levels a role may be homed at. */
  roles: RoleDefinition[]
  /** The application the role is of; left out for a role of the organization. */
  applicationId?: string
  /** The application's entity definitions, each of which the role may name. */
  definitions?: DefinitionOption[]
}>()

// a role of an application is homed at the tenant, and the rows' permissions are those of the definition's type
const APPLICATION_ROLE_HOME = 'tenant'
const ROWS_TYPE = 'entity_definition'

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', role: RoleDefinition): void
}>()

const toast = useToast()
const name = ref('')
const description = ref('')
const tenantAssignable = ref(false)
const home = ref<string | null>(null)
const selected = ref<string[]>([])
// the permissions held on single definitions, by the definition's id
const named = ref<Record<string, string[]>>({})
const saving = ref(false)

const homes = computed(() => homeTypes(props.roles).map(type => ({ type, label: typeLabel(type) })))

// the permissions of an entity's rows, which a role of an application holds on every entity or on the ones it names
const rowOptions = computed(() => (props.applicationId ? props.catalog[ROWS_TYPE] ?? [] : [])
    .map(permission => ({ name: permission, label: permissionLabel(splitPermission(permission).permission) })))

// the application's definitions, then any the role names that are not among them, by its id
const namedDefinitions = computed<DefinitionOption[]>(() => {
  const listed = props.definitions ?? []
  const others = Object.keys(props.role?.definitionPermissions ?? {})
      .filter(id => !listed.some(definition => definition.id === id))
      .map(id => ({ id, name: definitionName(id, listed) }))
  return [...listed, ...others]
})

// the permissions of the level and of the types below it, outermost first; for an application's role those of the
// tenant and of the types its services declare
const groups = computed(() => {
  let types: string[]
  if (props.applicationId) {
    types = Object.keys(props.catalog).filter(type => type !== ROWS_TYPE).sort()
  } else {
    types = home.value ? typesWithin(home.value, props.roles).filter(type => type in props.catalog) : []
  }
  return types.map(type => ({
    type,
    label: typeLabel(type),
    options: (props.catalog[type] ?? []).map(permission => ({ name: permission, label: permissionLabel(splitPermission(permission).permission) }))
  }))
})

// counts the times the drawer opened, so a save that completes after the drawer was closed and opened again
// leaves the editor it did not start open
let opening = 0

watch(() => props.visible, visible => {
  if (visible) {
    opening++
    name.value = props.role?.name ?? ''
    description.value = props.role?.description ?? ''
    tenantAssignable.value = props.role?.tenantAssignable ?? false
    home.value = props.applicationId ? APPLICATION_ROLE_HOME : props.role?.resourceType ?? homes.value[0]?.type ?? null
    selected.value = [...(props.role?.permissions ?? [])]
    named.value = Object.fromEntries(Object.entries(props.role?.definitionPermissions ?? {}).map(([id, held]) => [id, [...held]]))
  }
})

// what the role holds on the definitions it names, without what it holds on every entity
const definitionPermissions = computed(() => {
  const ret: Record<string, string[]> = {}
  for (const [id, held] of Object.entries(named.value)) {
    const alone = held.filter(permission => !selected.value.includes(permission))
    if (alone.length > 0) {
      ret[id] = alone
    }
  }
  return ret
})

const bundlesNothing = computed(() => selected.value.length === 0 && Object.keys(definitionPermissions.value).length === 0)

function holdsOn(definitionId: string, permission: string): boolean {
  return selected.value.includes(permission) || (named.value[definitionId] ?? []).includes(permission)
}

function setOn(definitionId: string, permission: string, held: boolean): void {
  const others = (named.value[definitionId] ?? []).filter(other => other !== permission)
  named.value = { ...named.value, [definitionId]: held ? [...others, permission] : others }
}

// a permission from outside the level cannot be saved, so changing the level drops it
watch(home, () => {
  const offered = new Set([...groups.value.flatMap(group => group.options), ...rowOptions.value].map(option => option.name))
  selected.value = selected.value.filter(permission => offered.has(permission))
})

async function submit(): Promise<void> {
  if (name.value.trim() === '' || bundlesNothing.value || home.value === null) {
    return
  }
  const thisOpening = opening
  saving.value = true
  try {
    const saved = await roleSourceOf(props.applicationId).saveRole({
      id: props.role?.id ?? null,
      name: name.value.trim(),
      description: description.value.trim() || null,
      builtIn: false,
      resourceType: home.value,
      grantableOn: [],
      permissions: selected.value,
      definitionPermissions: definitionPermissions.value,
      tenantAssignable: tenantAssignable.value
    })
    toast.add({ severity: 'success', summary: props.role?.id ? 'Role saved' : 'Role created', life: 4000 })
    emit('saved', saved)
    if (thisOpening === opening) {
      emit('update:visible', false)
    }
  } catch (err) {
    showErrorToast(toast, 'Failed to save role', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}
</script>
