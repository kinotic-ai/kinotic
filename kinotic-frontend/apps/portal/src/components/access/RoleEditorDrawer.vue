<template>
  <FormDrawer :visible="visible" :title="role?.id ? 'Edit role' : 'New role'"
              description="A role bundles permissions; a grant of it on a resource gives them there and on everything inside it."
              @close="emit('update:visible', false)">
    <form id="role-editor-form" @submit.prevent="submit">
      <div class="flex flex-col gap-5">
        <div>
          <label for="role-name" class="mb-2 block text-sm font-medium">Name</label>
          <InputText id="role-name" v-model="name" placeholder="Release manager" autocomplete="off" autofocus class="w-full" />
        </div>
        <div>
          <label for="role-description" class="mb-2 flex items-center justify-between text-sm font-medium">
            Description
            <span class="text-xs font-normal text-muted-color">Optional</span>
          </label>
          <Textarea id="role-description" v-model="description" rows="2" auto-resize placeholder="What the role is for" class="w-full" />
        </div>
        <div>
          <label for="role-level" class="mb-2 block text-sm font-medium">Level</label>
          <Select id="role-level" v-model="home" :options="homes" option-label="label" option-value="type" :disabled="!!role?.id" class="w-full" />
          <p class="mt-1.5 text-[0.8125rem] text-muted-color">The role bundles permissions of this level and below, and is granted here or above. It cannot be changed once created.</p>
        </div>
        <div>
          <span class="mb-2 block text-sm font-medium">Permissions</span>
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
              :disabled="name.trim() === '' || selected.length === 0" />
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
import { Kinotic } from '@kinotic-ai/core'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { FormDrawer, permissionLabel, showErrorToast, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import { homeTypes, typesWithin } from '@/util/access'

/**
 * Defines a custom role from the catalog, or reshapes one: its name, description and the permissions it
 * bundles, grouped by the resource type each is about.
 */
const props = defineProps<{
  visible: boolean
  /** The role to edit, or null for a new one. */
  role: RoleDefinition | null
  /** The catalog: the model names of every permission, by type. */
  catalog: Record<string, string[]>
  /** The organization's roles, whose built-in ones give the levels a role may be homed at. */
  roles: RoleDefinition[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', role: RoleDefinition): void
}>()

const toast = useToast()
const name = ref('')
const description = ref('')
const home = ref<string | null>(null)
const selected = ref<string[]>([])
const saving = ref(false)

const homes = computed(() => homeTypes(props.roles).map(type => ({ type, label: typeLabel(type) })))

// the permissions of the level and of the types below it, outermost first
const groups = computed(() => {
  const types = home.value ? typesWithin(home.value, props.roles).filter(type => type in props.catalog) : []
  return types.map(type => ({
    type,
    label: typeLabel(type),
    options: (props.catalog[type] ?? []).map(permission => ({ name: permission, label: permissionLabel(splitPermission(permission).permission) }))
  }))
})

watch(() => props.visible, visible => {
  if (visible) {
    name.value = props.role?.name ?? ''
    description.value = props.role?.description ?? ''
    home.value = props.role?.resourceType ?? homes.value[0]?.type ?? null
    selected.value = [...(props.role?.permissions ?? [])]
  }
})

// a permission from outside the level cannot be saved, so changing the level drops it
watch(home, () => {
  const offered = new Set(groups.value.flatMap(group => group.options.map(option => option.name)))
  selected.value = selected.value.filter(permission => offered.has(permission))
})

async function submit(): Promise<void> {
  if (name.value.trim() === '' || selected.value.length === 0 || home.value === null) {
    return
  }
  saving.value = true
  try {
    const saved = await Kinotic.permissions.saveRole({
      id: props.role?.id ?? null,
      name: name.value.trim(),
      description: description.value.trim() || null,
      builtIn: false,
      resourceType: home.value,
      grantableOn: [],
      permissions: selected.value
    })
    toast.add({ severity: 'success', summary: props.role?.id ? 'Role saved' : 'Role created', life: 4000 })
    emit('saved', saved)
    emit('update:visible', false)
  } catch (err) {
    showErrorToast(toast, 'Failed to save role', err, { life: 8000 })
  } finally {
    saving.value = false
  }
}
</script>
