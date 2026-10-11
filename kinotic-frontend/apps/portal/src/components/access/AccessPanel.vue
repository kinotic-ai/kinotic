<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DashboardSection :icon="KeyRound" :tint="tint" title="Grants made here" :count="loading ? undefined : here.length"
                      :description="`Who holds a role on this ${typeLabel(resource.type).toLowerCase()}, and so on everything inside it.`">
      <template #actions>
        <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDialogVisible = true" />
      </template>
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
      </div>
      <EmptyChartCharacter v-else-if="here.length === 0" class="py-6" title="No grants here yet"
                           :hint="canManageAccess ? 'Grant a member or a group a role to give them access to this and everything inside it.' : 'Access here comes only from grants made above.'" />
      <DataTable v-else :value="here">
        <Column header="Who" style="width: 40%">
          <template #body="{ data, index }">
            <SubjectCell :subject="data.subject" :label="labelOf(data.subject)" :index="index" />
          </template>
        </Column>
        <Column header="Role" style="width: 40%">
          <template #body="{ data }"><RoleCell :role="roleOf(data.roleId)" :role-id="data.roleId" /></template>
        </Column>
        <Column style="width: 20%">
          <template #body="{ data }">
            <div class="flex justify-end">
              <Button v-if="canManageAccess" label="Revoke" icon="pi pi-times" size="small" severity="danger" text @click="confirmRevoke(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </DashboardSection>

    <DashboardSection :icon="ArrowUpFromLine" :tint="tint" title="Inherited from above" :count="loading ? undefined : above.length"
                      description="Grants made on an ancestor that reach this; each is revoked where it was made.">
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton height="2.5rem" />
      </div>
      <EmptyChartCharacter v-else-if="above.length === 0" class="py-6" title="Nothing reaches here from above"
                           hint="A grant on the application or the organization would show here." />
      <DataTable v-else :value="above">
        <Column header="Who" style="width: 36%">
          <template #body="{ data, index }">
            <SubjectCell :subject="data.subject" :label="labelOf(data.subject)" :index="index" />
          </template>
        </Column>
        <Column header="Role" style="width: 32%">
          <template #body="{ data }"><RoleCell :role="roleOf(data.roleId)" :role-id="data.roleId" /></template>
        </Column>
        <Column header="Granted on" style="width: 32%">
          <template #body="{ data }">
            <TableChip :icon="MapPin" :to="accessPath(data.resource, context) ?? undefined">
              {{ typeLabel(data.resource.type) }} {{ data.resource.id }}
            </TableChip>
          </template>
        </Column>
      </DataTable>
    </DashboardSection>

    <AccessCheckPanel :resource="resource" :tint="tint" :members="members" :groups="groups" :roles="roles" :catalog="catalog"
                      :label-of="labelOf" :context="context" />

    <GrantAccessDialog v-model:visible="grantDialogVisible" :resource="resource" :roles="fittingRoles" :members="members" :groups="groups"
                       @granted="onGranted" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { ArrowUpFromLine, KeyRound, MapPin } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { Grant, Resource, RoleDefinition } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, RoleCell, SubjectCell, TableChip, showErrorToast, typeLabel } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import { accessPath, grantsAbove, grantsOn, roleFits } from '@/util/access'
import AccessCheckPanel from './AccessCheckPanel.vue'
import GrantAccessDialog from './GrantAccessDialog.vue'
import { useSubjects } from './useSubjects'

/**
 * The Access tab of a resource: the grants made on it, the grants reaching it from its ancestors with a link to
 * where each was made, a check of anyone's access, and, for a member who manages the organization's access,
 * granting and revoking.
 */
const props = defineProps<{
  resource: Resource
  /** Classes of the sections' icon tiles, one of TINTS. */
  tint: string
  /** The ids of the ancestors the caller is looking at, for the links to where a grant was made. */
  applicationId?: string
  projectId?: string
}>()

const toast = useToast()
const confirm = useConfirm()
const { canManageAccess } = useAccess()
const { members, groups, load: loadSubjects, labelOf } = useSubjects()

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const catalog = ref<Record<string, string[]>>({})
const loading = ref(true)
const error = ref<string | null>(null)
const grantDialogVisible = ref(false)

const context = computed(() => ({ applicationId: props.applicationId, projectId: props.projectId }))
const here = computed(() => grantsOn(grants.value, props.resource))
const above = computed(() => grantsAbove(grants.value, props.resource))
const fittingRoles = computed(() => roles.value.filter(role => roleFits(role, props.resource.type)))

watch(() => [props.resource.type, props.resource.id], load, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [loadedGrants, loadedRoles, loadedCatalog] = await Promise.all([
      Kinotic.permissions.findGrants(props.resource),
      Kinotic.permissions.findRoles(),
      Kinotic.permissions.findPermissions(),
      loadSubjects()
    ])
    grants.value = loadedGrants
    roles.value = loadedRoles
    catalog.value = loadedCatalog
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function roleOf(roleId: string): RoleDefinition | undefined {
  return roles.value.find(role => role.id === roleId)
}

function onGranted(grant: Grant): void {
  grants.value = [...grants.value, grant]
}

function confirmRevoke(grant: Grant): void {
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${roleOf(grant.roleId)?.name ?? grant.roleId} from ${labelOf.value(grant.subject)} here? They keep whatever other grants reach them.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Keep', severity: 'secondary', outlined: true },
    accept: () => revoke(grant)
  })
}

async function revoke(grant: Grant): Promise<void> {
  try {
    await Kinotic.permissions.revoke(props.resource, grant.id)
    grants.value = grants.value.filter(listed => listed.id !== grant.id)
    toast.add({ severity: 'success', summary: 'Access revoked', life: 4000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to revoke access', err, { life: 8000 })
  }
}
</script>
