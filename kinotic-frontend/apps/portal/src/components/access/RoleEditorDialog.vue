<template>
  <FormDialog :visible="visible" :icon="ShieldCheck" :title="role?.id ? 'Edit role' : 'New role'"
              description="A role bundles permissions; a grant of it on a resource gives them there and on everything inside it."
              @update:visible="emit('update:visible', $event)" @submit="submit">
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
        <span class="mb-2 block text-sm font-medium">Permissions</span>
        <div class="max-h-[50vh] overflow-y-auto rounded-xl border border-surface-200 dark:border-surface-700">
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
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">{{ selected.length }} selected. Deleting implies editing and editing implies viewing.</p>
      </div>
    </div>

    <template #footer>
      <Button type="button" label="Cancel" severity="secondary" outlined @click="emit('update:visible', false)" />
      <Button type="submit" :label="role?.id ? 'Save role' : 'Create role'" icon="pi pi-check" :loading="saving"
              :disabled="name.trim() === '' || selected.length === 0" />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { ShieldCheck } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { FormDialog, showErrorToast } from '@kinotic-ai/frontend-common'
import { RESOURCE_TYPES, permissionLabel, splitPermission, typeLabel } from '@/util/access'

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
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved', role: RoleDefinition): void
}>()

const toast = useToast()
const name = ref('')
const description = ref('')
const selected = ref<string[]>([])
const saving = ref(false)

// the organization's tree first, then any other type a service declares
const groups = computed(() => {
  const types = [...RESOURCE_TYPES.filter(type => type in props.catalog),
                 ...Object.keys(props.catalog).filter(type => !(RESOURCE_TYPES as readonly string[]).includes(type)).sort()]
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
    selected.value = [...(props.role?.permissions ?? [])]
  }
})

async function submit(): Promise<void> {
  if (name.value.trim() === '' || selected.value.length === 0) {
    return
  }
  saving.value = true
  try {
    const saved = await Kinotic.permissions.saveRole({
      id: props.role?.id ?? null,
      name: name.value.trim(),
      description: description.value.trim() || null,
      builtIn: false,
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
