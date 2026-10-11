<template>
  <div class="flex flex-col">
    <PageHeader title="Access" description="Who runs the platform: the grants made on it reach every organization and worker node." />

    <NoAccessState v-if="refused" description="No grant you hold reaches the platform's access. An administrator of the platform can grant you access from this page." />
    <div v-else class="flex flex-col gap-4">
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DashboardSection :icon="KeyRound" :tint="TINTS.ink" title="Grants on the platform" :count="loading ? undefined : shown.length"
                        :description="subjectFilter ? `The grants of ${labelOf(subjectFilter)}.` : 'Who holds a role on the platform, and so on everything on it.'">
        <template #actions>
          <Button v-if="subjectFilter" label="Show everyone" icon="pi pi-filter-slash" size="small" severity="secondary" outlined @click="clearSubject" />
          <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDialogVisible = true" />
        </template>
        <div v-if="loading" class="flex flex-col gap-2 p-5">
          <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
        </div>
        <EmptyChartCharacter v-else-if="shown.length === 0" class="py-6" :title="subjectFilter ? 'No grants for them yet' : 'No grants yet'"
                             :hint="canManageAccess ? 'Grant an operator or a machine a role to give them access to the platform.' : 'Access to the platform comes from grants made here.'" />
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

      <AccessCheckPanel :tint="TINTS.ink" :operators="operators" :machines="machines" :roles="roles" :catalog="catalog"
                        :label-of="labelOf" :initial-subject-id="subjectFilter?.id" />

      <GrantAccessDialog v-model:visible="grantDialogVisible" :roles="orderedRoles" :operators="operators" :machines="machines"
                         @granted="onGranted" />
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
import { type Grant, type RoleDefinition, type Subject, SubjectKind } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, NoAccessState, PageHeader, RoleCell, SubjectCell, TINTS,
         errorMessage, isAuthorizationError, showErrorToast, splitPermission } from '@kinotic-ai/frontend-common'
import { useAccess } from '@/composables/useAccess'
import AccessCheckPanel from '@/components/access/AccessCheckPanel.vue'
import GrantAccessDialog from '@/components/access/GrantAccessDialog.vue'
import { useSubjects } from '@/components/access/useSubjects'

/**
 * The platform's Access page: the grants made on the platform, a grant of a role to an operator or a machine,
 * and a check that explains anyone's access. Opened for one subject from the Users or Machines page, it shows
 * that subject's grants alone.
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const { canManageAccess } = useAccess()
const { operators, machines, load: loadSubjects, optionOf, labelOf } = useSubjects()

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const loading = ref(true)
const refused = ref(false)
const error = ref<string | null>(null)
const grantDialogVisible = ref(false)

/** The subject the page was opened for, from its query, or null for everyone's grants. */
const subjectFilter = computed<Subject | null>(() => {
  const id = route.query.subject
  return typeof id === 'string' && id.length > 0 ? { kind: SubjectKind.USER, id } : null
})

const shown = computed(() => subjectFilter.value
    ? grants.value.filter(grant => grant.subject.id === subjectFilter.value?.id)
    : grants.value)

// the platform's own roles lead the picker, the organization tree's follow
const orderedRoles = computed(() => [...roles.value].sort((a, b) => Number(platformRole(b)) - Number(platformRole(a)) || a.name.localeCompare(b.name)))

/** The platform's own permissions: what explain answers for, read from the roles that bundle them. */
const catalog = computed(() => {
  const names = new Set<string>()
  for (const role of roles.value) {
    for (const permission of role.permissions) {
      if (splitPermission(permission).type === 'platform') {
        names.add(permission)
      }
    }
  }
  return [...names].sort()
})

watch(() => route.name, name => { if (name === 'platform-access') load() }, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [found, defined] = await Promise.all([
      Kinotic.systemAccess.findGrants(),
      Kinotic.systemAccess.findRoles(),
      loadSubjects()
    ])
    grants.value = found
    roles.value = defined
    refused.value = false
  } catch (err) {
    refused.value = isAuthorizationError(err)
    error.value = refused.value ? null : errorMessage(err, 'Failed to load the platform\'s access')
  } finally {
    loading.value = false
  }
}

function platformRole(role: RoleDefinition): boolean {
  const id = role.id ?? ''
  return id.startsWith('platform.') || id.startsWith('vm_node.')
}

function roleOf(roleId: string): RoleDefinition | undefined {
  return roles.value.find(role => role.id === roleId)
}

function clearSubject(): void {
  router.replace({ name: 'platform-access' })
}

function onGranted(grant: Grant): void {
  grants.value = [...grants.value, grant]
}

function confirmRevoke(grant: Grant): void {
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${roleOf(grant.roleId)?.name ?? grant.roleId} from ${labelOf.value(grant.subject)}? They lose every permission the role gave them on the platform.`,
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
