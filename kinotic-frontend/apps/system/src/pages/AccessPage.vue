<template>
  <div class="flex flex-col">
    <PageHeader title="Access" description="Who runs the system: the grants made on it reach every organization and worker node." />

    <NoAccessState v-if="refused" description="No grant you hold reaches the system's access. An administrator of the system can grant you access from this page." />
    <div v-else class="flex flex-col gap-4">
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DashboardSection :icon="KeyRound" :tint="TINTS.ink" title="Grants on the system" :count="loading ? undefined : shown.length"
                        :description="subjectFilter ? `The grants of ${labelOf(subjectFilter)}.` : 'Who holds a role on the system, and so on everything on it.'">
        <template #actions>
          <Button v-if="subjectFilter" label="Show everyone" icon="pi pi-filter-slash" size="small" severity="secondary" outlined @click="clearSubject" />
          <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDrawerVisible = true" />
        </template>
        <div v-if="loading" class="flex flex-col gap-2 p-5">
          <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
        </div>
        <EmptyChartCharacter v-else-if="shown.length === 0" class="py-6" :title="subjectFilter ? 'No grants for them yet' : 'No grants yet'"
                             :hint="canManageAccess ? 'Grant a system user or a machine a role to give them access to the system.' : 'Access to the system comes from grants made here.'" />
        <DataTable v-else :value="shown">
          <Column header="Who" style="width: 40%">
            <template #body="{ data, index }">
              <SubjectCell :subject="data.subject" :label="labelOf(data.subject)" :detail="optionOf(data.subject)?.staff" :index="index" />
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

      <AccessCheckPanel :tint="TINTS.ink"
                        description="Ask whether a system user or a machine holds one of the system's permissions, and which grants give it."
                        :subject-groups="[{ label: 'System users', items: systemUsers }, { label: 'Machines', items: machines }]"
                        subject-placeholder="System user or machine" :permission-options="permissionOptions" :roles="roles"
                        :label-of="labelOf" :initial-subject="subjectFilter ?? undefined"
                        held-without-grant="Held through nothing a grant explains." no-grant="No grant on the system gives the permission."
                        :explain="explain">
        <template #grant-resource>
          <span class="text-muted-color">on the system</span>
        </template>
      </AccessCheckPanel>

      <GrantAccessDrawer v-model:visible="grantDrawerVisible"
                         description="Give a system user or a machine a role on the system, which reaches every organization and worker node on it."
                         :kinds="grantKinds" :roles="roles" role-hint="The role's permissions reach the system and everything on it."
                         :grant="grant" @granted="onGranted" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Skeleton from 'primevue/skeleton'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { KeyRound } from '@lucide/vue'
import { Kinotic } from '@kinotic-ai/core'
import { type Grant, type RoleDefinition, type Subject, SubjectType } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, NoAccessState, PageHeader, TINTS,
         errorMessage, isAuthorizationError, permissionLabel, showErrorToast, splitPermission } from '@kinotic-ai/frontend-common'
import { AccessCheckPanel, type CheckedAccess, GrantAccessDrawer, RoleCell, SubjectCell, type SubjectKind } from '@kinotic-ai/frontend-common/management'
import { useSubjects } from '@/components/access/useSubjects'
import { SYSTEM_USER_STATE } from '@/states/SystemUserState'

/**
 * The system's Access page: the grants made on the system, a grant of a role to a system user or a machine,
 * and a check that explains anyone's access. Opened for one subject from the Users or Machines page, it shows
 * that subject's grants alone.
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const { systemUsers, machines, load: loadSubjects, optionOf, labelOf } = useSubjects()

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const canManageAccess = ref(false)
const loading = ref(true)
const refused = ref(false)
const error = ref<string | null>(null)
const grantDrawerVisible = ref(false)

/** The subject the page was opened for, from its query, or null for everyone's grants. */
const subjectFilter = computed<Subject | null>(() => {
  const id = route.query.subject
  return typeof id === 'string' && id.length > 0 ? { type: SubjectType.USER, id } : null
})

const shown = computed(() => subjectFilter.value
    ? grants.value.filter(grant => grant.subject.id === subjectFilter.value?.id)
    : grants.value)

/** The system's own permissions: what explain answers for, read from the roles that bundle them. */
const catalog = computed(() => {
  const names = new Set<string>()
  for (const role of roles.value) {
    for (const permission of role.permissions) {
      if (splitPermission(permission).type === 'system') {
        names.add(permission)
      }
    }
  }
  return [...names].sort()
})

const permissionOptions = computed(() => catalog.value.map(name => ({ name, label: permissionLabel(splitPermission(name).permission) })))

const grantKinds = computed<SubjectKind[]>(() => [
  { label: 'System user', options: systemUsers.value, placeholder: 'Choose a system user', emptyMessage: 'No system users' },
  { label: 'Machine', options: machines.value, placeholder: 'Choose a machine', emptyMessage: 'No machines yet' }
])

watch(() => route.name, name => { if (name === 'system-access') load() }, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [found, defined, managing] = await Promise.all([
      Kinotic.systemAccess.findGrants(),
      Kinotic.systemAccess.findRoles(),
      canManageSelf(),
      loadSubjects()
    ])
    grants.value = found
    roles.value = defined
    canManageAccess.value = managing
    refused.value = false
  } catch (err) {
    refused.value = isAuthorizationError(err)
    error.value = refused.value ? null : errorMessage(err, 'Failed to load the system\'s access')
  } finally {
    loading.value = false
  }
}

// whether the signed-in system user may grant and revoke, asked of the system's access service about themself
async function canManageSelf(): Promise<boolean> {
  const systemUserId = SYSTEM_USER_STATE.connectedInfo?.participant?.id
  let ret = false
  if (systemUserId) {
    ret = (await Kinotic.systemAccess.explain({ type: SubjectType.USER, id: systemUserId }, 'can_manage_access')).allowed
  }
  return ret
}

function roleOf(roleId: string): RoleDefinition | undefined {
  return roles.value.find(role => role.id === roleId)
}

function clearSubject(): void {
  router.replace({ name: 'system-access' })
}

async function explain(subject: Subject, permission: string): Promise<CheckedAccess> {
  const split = splitPermission(permission)
  const explanation = await Kinotic.systemAccess.explain(subject, split.permission)
  return { explanation, asked: `${permissionLabel(split.permission).toLowerCase()} on the system` }
}

function grant(subject: Subject, roleId: string): Promise<Grant> {
  return Kinotic.systemAccess.grant(subject, roleId)
}

function onGranted(grant: Grant): void {
  grants.value = [...grants.value, grant]
}

function confirmRevoke(grant: Grant): void {
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${roleOf(grant.roleId)?.name ?? grant.roleId} from ${labelOf.value(grant.subject)}? They lose every permission the role gave them on the system.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.systemAccess.revoke(grant.id)
        grants.value = grants.value.filter(other => other.id !== grant.id)
        toast.add({ severity: 'success', summary: 'Access revoked', life: 4000 })
      } catch (err) {
        showErrorToast(toast, 'Failed to revoke access', err, { life: 8000 })
      }
    }
  })
}
</script>
