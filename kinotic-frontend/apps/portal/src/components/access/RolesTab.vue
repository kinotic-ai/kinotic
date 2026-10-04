<template>
  <div class="flex flex-col gap-4 pt-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DashboardSection :icon="ShieldCheck" :tint="TINTS.green" title="Roles" :count="loading ? undefined : roles.length"
                      description="A role bundles permissions. The built-in roles come with the platform; your organization defines its own from the same catalog.">
      <template #actions>
        <Button v-if="canManageAccess" label="New role" icon="pi pi-plus" size="small" @click="edit(null)" />
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
            </span>
          </template>
        </Column>
        <Column header="Permissions" style="width: 50%">
          <template #body="{ data }">
            <span class="flex flex-wrap gap-1">
              <TableChip v-for="permission in data.permissions" :key="permission">{{ chip(permission) }}</TableChip>
            </span>
          </template>
        </Column>
        <Column style="width: 18%">
          <template #body="{ data }">
            <div v-if="canManageAccess && !data.builtIn" class="flex justify-end gap-1">
              <Button label="Edit" icon="pi pi-pencil" size="small" severity="secondary" text @click="edit(data)" />
              <Button label="Delete" icon="pi pi-trash" size="small" severity="danger" text @click="confirmDelete(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </DashboardSection>

    <RoleEditorDialog v-model:visible="editorVisible" :role="editing" :catalog="catalog" @saved="onSaved" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ShieldCheck } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { RoleDefinition } from '@kinotic-ai/management-api'
import { DashboardSection, TINTS, TableChip, showErrorToast } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import { permissionLabel, splitPermission, typeLabel } from '@/util/access'
import RoleEditorDialog from './RoleEditorDialog.vue'

/**
 * The organization's roles: the built-in ones, read-only, and the custom ones a member who manages access
 * defines, edits and deletes once no grant holds them.
 */
const toast = useToast()
const confirm = useConfirm()
const { canManageAccess } = useAccess()

const roles = ref<RoleDefinition[]>([])
const catalog = ref<Record<string, string[]>>({})
const loading = ref(true)
const error = ref<string | null>(null)
const editorVisible = ref(false)
const editing = ref<RoleDefinition | null>(null)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [loadedRoles, loadedCatalog] = await Promise.all([Kinotic.permissions.findRoles(), Kinotic.permissions.findPermissions()])
    // the built-in roles first, then the organization's own, each set by name
    roles.value = [...loadedRoles].sort((a, b) => Number(b.builtIn) - Number(a.builtIn) || a.name.localeCompare(b.name))
    catalog.value = loadedCatalog
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function chip(permission: string): string {
  const split = splitPermission(permission)
  return `${typeLabel(split.type)}: ${permissionLabel(split.permission).toLowerCase()}`
}

function edit(role: RoleDefinition | null): void {
  editing.value = role
  editorVisible.value = true
}

function onSaved(): void {
  void load()
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
    await Kinotic.permissions.deleteRole(role.id!)
    roles.value = roles.value.filter(listed => listed.id !== role.id)
    toast.add({ severity: 'success', summary: 'Role deleted', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to delete role', err, { life: 8000 })
  }
}
</script>
