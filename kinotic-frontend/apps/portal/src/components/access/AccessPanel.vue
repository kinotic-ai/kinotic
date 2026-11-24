<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <DashboardSection :icon="KeyRound" :tint="tint" title="Grants made here" :count="loading ? undefined : here.length"
                      :description="`Who holds a role on this ${typeLabel(resource.type).toLowerCase()}, and so on everything inside it.`">
      <template #actions>
        <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDrawerVisible = true" />
      </template>
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
      </div>
      <EmptyChartCharacter v-else-if="here.length === 0" class="py-6" title="No grants here yet"
                           :hint="canManageAccess ? 'Grant a member or a group a role to give them access to this and everything inside it.' : 'Access here comes only from grants made above.'" />
      <GrantsTable v-else :grants="here" :roles="roles" :label-of="labelOf" :revoke="canManageAccess ? revoke : undefined"
                   :revoke-where="() => 'here'" revoke-consequence="They keep whatever other grants reach them." @revoked="onRevoked" />
    </DashboardSection>

    <DashboardSection :icon="ArrowUpFromLine" :tint="tint" title="Inherited from above" :count="loading ? undefined : above.length"
                      description="Grants made on an ancestor that reach this; each is revoked where it was made.">
      <div v-if="loading" class="flex flex-col gap-2 p-5">
        <Skeleton height="2.5rem" />
      </div>
      <EmptyChartCharacter v-else-if="above.length === 0" class="py-6" title="Nothing reaches here from above"
                           hint="A grant on the application or the organization would show here." />
      <GrantsTable v-else :grants="above" :roles="roles" :label-of="labelOf" where-header="Granted on">
        <template #where="{ grant }">
          <TableChip :icon="MapPin" :to="accessPath(grant.resource, context) ?? undefined">
            {{ typeLabel(grant.resource.type) }} {{ grant.resource.id }}
          </TableChip>
        </template>
      </GrantsTable>
    </DashboardSection>

    <AccessCheckPanel :tint="tint" description="Ask whether someone holds a permission here, and which grants give it."
                      :subject-groups="[{ label: 'Members', options: members, search: searchMembers }, { label: 'Groups', options: groups }]"
                      subject-placeholder="Member or group" :permission-options="permissionOptions" :roles="roles" :label-of="labelOf"
                      held-without-grant="Held through nothing a grant explains, such as being a member of the organization."
                      :no-grant="`No grant on this ${resourceTypeLabel} or above it gives the permission.`" :explain="explain">
      <template #grant-resource="{ grant }">
        <span class="text-muted-color">on</span>
        <TableChip :icon="MapPin" :to="accessPath(grant.resource, context) ?? undefined">{{ typeLabel(grant.resource.type) }} {{ grant.resource.id }}</TableChip>
      </template>
    </AccessCheckPanel>

    <GrantAccessDrawer v-model:visible="grantDrawerVisible" :description="`Give a member or a group a role on this ${resourceTypeLabel}.`"
                       :tabs="grantTabs" :roles="fittingRoles"
                       :role-hint="`The role's permissions reach this ${resourceTypeLabel} and everything inside it.`"
                       :grant="grant" @granted="onGranted" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import { ArrowUpFromLine, KeyRound, MapPin } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import type { Grant, Resource, RoleDefinition, Subject } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, TableChip, latestOnly, permissionLabel, splitPermission, typeLabel } from '@kinotic-ai/frontend-common'
import { AccessCheckPanel, type CheckedAccess, GrantAccessDrawer, GrantsTable, type SubjectPickerTab } from '@kinotic-ai/frontend-common/management'
import { useAccess } from '@/composables/useAccess'
import { accessPath, grantsAbove, grantsOn } from '@/util/access'
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

const { canManageAccess } = useAccess()
const { members, groups, load: loadSubjects, searchMembers, labelOf } = useSubjects()

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const catalog = ref<Record<string, string[]>>({})
const loading = ref(true)
const error = ref<string | null>(null)
const grantDrawerVisible = ref(false)

const context = computed(() => ({ applicationId: props.applicationId, projectId: props.projectId }))
const here = computed(() => grantsOn(grants.value, props.resource))
const above = computed(() => grantsAbove(grants.value, props.resource))
const fittingRoles = computed(() => roles.value.filter(role => role.grantableOn.includes(props.resource.type)))
const resourceTypeLabel = computed(() => typeLabel(props.resource.type).toLowerCase())

// explain answers for a permission of the resource's own type, checked on the resource
const permissionOptions = computed(() => (catalog.value[props.resource.type] ?? []).map(name => ({
  name,
  label: permissionLabel(splitPermission(name).permission)
})))

const grantTabs = computed<SubjectPickerTab[]>(() => [
  { label: 'Member', options: members.value, search: searchMembers, placeholder: 'Name or email of a member', emptyMessage: 'No member matches' },
  { label: 'Group', options: groups.value, placeholder: 'Choose a group', emptyMessage: 'No groups yet' }
])

// the panel is kept when the header switches the resource in place, so a load may start while an earlier one is
// in flight; only the latest load's answer, or failure, reaches the panel, and the grants are emptied as a load
// starts, so a load that fails shows none of another resource's
const startLoad = latestOnly()

watch(() => [props.resource.type, props.resource.id], load, { immediate: true })

async function load(): Promise<void> {
  const isLatest = startLoad()
  loading.value = true
  error.value = null
  grants.value = []
  try {
    const [loadedGrants, loadedRoles, loadedCatalog] = await Promise.all([
      Kinotic.permissions.findGrants(props.resource),
      Kinotic.permissions.findRoles(),
      Kinotic.permissions.findPermissions(),
      loadSubjects()
    ])
    if (!isLatest()) {
      return
    }
    grants.value = loadedGrants
    roles.value = loadedRoles
    catalog.value = loadedCatalog
  } catch (err) {
    if (!isLatest()) {
      return
    }
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    if (isLatest()) {
      loading.value = false
    }
  }
}

async function explain(subject: Subject, permission: string): Promise<CheckedAccess> {
  const split = splitPermission(permission)
  const explanation = await Kinotic.permissions.explain(subject, split.permission, props.resource)
  return { explanation, asked: `${permissionLabel(split.permission).toLowerCase()} on this ${resourceTypeLabel.value}` }
}

function grant(subject: Subject, roleId: string): Promise<Grant> {
  return Kinotic.permissions.grant(subject, roleId, props.resource)
}

function onGranted(grant: Grant): void {
  grants.value = [...grants.value, grant]
}

function revoke(grant: Grant): Promise<void> {
  return Kinotic.permissions.revoke(props.resource, grant.id)
}

function onRevoked(grant: Grant): void {
  grants.value = grants.value.filter(listed => listed.id !== grant.id)
}
</script>
