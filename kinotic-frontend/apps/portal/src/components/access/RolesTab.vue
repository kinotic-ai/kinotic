<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DashboardSection :icon="ShieldCheck" :tint="tint ?? TINTS.green" title="Roles" :count="loading ? undefined : roles.length"
                      :description="applicationId
                          ? 'A role says what its holders may do with each entity and each service of the application. The built-in roles reach every entity; the application\'s own name the ones they reach.'
                          : 'A role bundles permissions. The built-in roles come with the system; your organization defines its own from the same catalog.'">
      <template #actions>
        <Button v-if="manages" label="New role" icon="pi pi-plus" size="small" @click="edit(null)" />
      </template>
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton v-for="n in 3" :key="n" height="2.5rem" />
      </div>
      <DataTable v-else :value="roles">
        <Column header="Role" style="width: 32%">
          <template #body="{ data }">
            <span class="flex flex-col">
              <span class="flex items-center gap-2 text-sm text-surface-950 dark:text-surface-0">
                {{ data.name }}
                <Tag v-if="data.builtIn" value="Built-in" severity="secondary" class="!text-[10px]" />
              </span>
              <span v-if="data.description" class="text-xs text-muted-color">{{ data.description }}</span>
              <span v-if="applicationId && data.tenantAssignable" class="text-xs text-muted-color">Granted by tenant administrators</span>
            </span>
          </template>
        </Column>
        <Column header="Permissions" style="width: 50%">
          <template #body="{ data }">
            <span class="flex flex-wrap gap-1">
              <TableChip v-for="permission in data.permissions" :key="permission">{{ describePermission(permission) }}</TableChip>
              <TableChip v-for="(held, definitionId) in data.definitionPermissions" :key="definitionId">{{ describeDefinition(String(definitionId), held) }}</TableChip>
            </span>
          </template>
        </Column>
        <Column style="width: 18%">
          <template #body="{ data }">
            <div v-if="manages && !data.builtIn" class="flex justify-end gap-1">
              <Button label="Edit" icon="pi pi-pencil" size="small" severity="secondary" text @click="edit(data)" />
              <Button label="Delete" icon="pi pi-trash" size="small" severity="danger" text @click="confirmDelete(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </DashboardSection>

    <RoleEditorDrawer v-model:visible="editorVisible" :role="editing" :catalog="catalog" :roles="roles" :application-id="applicationId"
                      :definitions="definitions" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ShieldCheck } from '@lucide/vue'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { DashboardSection, TINTS, TableChip, describePermission, permissionLabel, showErrorToast, splitPermission } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import { type DefinitionOption, definitionName } from './applicationResources'
import RoleEditorDrawer from './RoleEditorDrawer.vue'
import { roleSourceOf } from './roleSource'

/**
 * The roles of the organization, or of one application: the built-in ones, read-only, and the custom ones a
 * member who manages access defines, edits and deletes once no grant holds them.
 */
const props = defineProps<{
  /** The application whose roles these are; left out for the organization's. */
  applicationId?: string
  /** The application's entity definitions, which a role of its own names. */
  definitions?: DefinitionOption[]
  /** Whether the member manages the application's access; the organization's is read here. */
  canManage?: boolean
  tint?: string
}>()

const emit = defineEmits<{
  /** A role was created, edited or deleted. */
  (e: 'changed'): void
}>()

const toast = useToast()
const confirm = useConfirm()
const { canManageAccess } = useAccess()
const source = computed(() => roleSourceOf(props.applicationId))
const manages = computed(() => props.applicationId ? props.canManage === true : canManageAccess.value)

const roles = ref<RoleDefinition[]>([])
const catalog = ref<Record<string, string[]>>({})
const loading = ref(true)
const error = ref<string | null>(null)
const editorVisible = ref(false)
const editing = ref<RoleDefinition | null>(null)

// the page is kept when the header switches the application in place
watch(() => props.applicationId, load, { immediate: true })

async function load(): Promise<void> {
  const loadingFrom = source.value
  loading.value = true
  error.value = null
  try {
    const [loadedRoles, loadedCatalog] = await Promise.all([loadingFrom.findRoles(), loadingFrom.findPermissions()])
    if (loadingFrom === source.value) {
      // the built-in roles first, then the custom ones, each set by name
      roles.value = [...loadedRoles].sort((a, b) => Number(b.builtIn) - Number(a.builtIn) || a.name.localeCompare(b.name))
      catalog.value = loadedCatalog
    }
  } catch (err) {
    if (loadingFrom === source.value) {
      error.value = err instanceof Error ? err.message : String(err)
    }
  } finally {
    if (loadingFrom === source.value) {
      loading.value = false
    }
  }
}

/** What a role holds on one definition it names: Invoice: read, edit. */
function describeDefinition(definitionId: string, held: string[]): string {
  const verbs = held.map(permission => permissionLabel(splitPermission(permission).permission).toLowerCase())
  return `${definitionName(definitionId, props.definitions ?? [])}: ${verbs.join(', ')}`
}

function edit(role: RoleDefinition | null): void {
  editing.value = role
  editorVisible.value = true
}

function onSaved(): void {
  void load()
  emit('changed')
}

function confirmDelete(role: RoleDefinition): void {
  confirm.require({
    header: 'Delete role',
    message: `Delete ${role.name}? A role a grant still holds cannot be deleted; revoke those grants first.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
    accept: () => deleteRole(role)
  })
}

async function deleteRole(role: RoleDefinition): Promise<void> {
  try {
    await source.value.deleteRole(role.id!)
    roles.value = roles.value.filter(listed => listed.id !== role.id)
    emit('changed')
    toast.add({ severity: 'success', summary: 'Role deleted', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to delete role', err, { life: 8000 })
  }
}
</script>
