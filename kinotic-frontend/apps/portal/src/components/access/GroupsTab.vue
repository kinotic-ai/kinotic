<template>
  <div class="flex flex-col gap-4 pt-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DashboardSection :icon="UsersRound" :tint="TINTS.green" title="Groups" :count="loading ? undefined : groups.length"
                      description="Collect members so one grant reaches all of them; a grant made to a group reaches whoever is in it at the time.">
      <template #actions>
        <Button v-if="canManageAccess" label="New group" icon="pi pi-plus" size="small" @click="edit(null)" />
      </template>
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
      </div>
      <EmptyChartCharacter v-else-if="groups.length === 0" class="py-6" title="No groups yet"
                           :hint="canManageAccess ? 'Create a group, add members to it, and grant it a role where they all need access.' : 'An administrator can create groups.'" />
      <DataTable v-else :value="groups">
        <Column header="Group" style="width: 40%">
          <template #body="{ data, index }">
            <span class="flex items-center gap-3">
              <InitialsTile :name="data.name" :index="index" />
              <span class="flex min-w-0 flex-col">
                <span class="truncate text-sm text-surface-950 dark:text-surface-0">{{ data.name }}</span>
                <span v-if="data.description" class="truncate text-xs text-muted-color">{{ data.description }}</span>
              </span>
            </span>
          </template>
        </Column>
        <Column header="Created" style="width: 24%">
          <template #body="{ data }">{{ data.created ? formatDate(data.created) : '—' }}</template>
        </Column>
        <Column style="width: 36%">
          <template #body="{ data }">
            <div class="flex justify-end gap-1">
              <Button label="Members" icon="pi pi-users" size="small" severity="secondary" text @click="openMembers(data)" />
              <template v-if="canManageAccess">
                <Button label="Rename" icon="pi pi-pencil" size="small" severity="secondary" text @click="edit(data)" />
                <Button label="Delete" icon="pi pi-trash" size="small" severity="danger" text @click="confirmDelete(data)" />
              </template>
            </div>
          </template>
        </Column>
      </DataTable>
    </DashboardSection>

    <GroupDialog v-model:visible="editorVisible" :group="editing" @saved="load" />
    <GroupMembersDialog v-model:visible="membersVisible" :group="opened" :candidates="members" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { UsersRound } from '@lucide/vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import type { Group } from '@kinotic-ai/management-api'
import { DashboardSection, DatetimeUtil, EmptyChartCharacter, InitialsTile, TINTS, showErrorToast } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import GroupDialog from './GroupDialog.vue'
import GroupMembersDialog from './GroupMembersDialog.vue'
import { useSubjects } from './useSubjects'

// an organization's groups are few; the first page holds them all
const GROUPS_PAGE_SIZE = 500

/** The organization's groups, their members, and, for a member who manages access, creating, renaming and deleting them. */
const toast = useToast()
const confirm = useConfirm()
const { canManageAccess } = useAccess()
const { members, load: loadSubjects } = useSubjects()

const groups = ref<Group[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const editorVisible = ref(false)
const editing = ref<Group | null>(null)
const membersVisible = ref(false)
const opened = ref<Group | null>(null)

const formatDate = DatetimeUtil.formatLocaleDate

onMounted(async () => {
  await load()
  try {
    await loadSubjects()
  } catch (err) {
    showErrorToast(toast, 'Failed to load the organization\'s members', err, { life: 8000 })
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const page = await Kinotic.permissions.findGroups(Pageable.create(0, GROUPS_PAGE_SIZE))
    groups.value = page.content ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function edit(group: Group | null): void {
  editing.value = group
  editorVisible.value = true
}

function openMembers(group: Group): void {
  opened.value = group
  membersVisible.value = true
}

function confirmDelete(group: Group): void {
  confirm.require({
    header: 'Delete group',
    message: `Delete ${group.name}? A group a grant still holds cannot be deleted; revoke those grants first.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
    accept: () => deleteGroup(group)
  })
}

async function deleteGroup(group: Group): Promise<void> {
  try {
    await Kinotic.permissions.deleteGroup(group.id!)
    groups.value = groups.value.filter(listed => listed.id !== group.id)
    toast.add({ severity: 'success', summary: 'Group deleted', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to delete group', err, { life: 8000 })
  }
}
</script>
