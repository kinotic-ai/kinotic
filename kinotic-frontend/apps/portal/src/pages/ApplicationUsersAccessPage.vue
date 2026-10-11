<template>
  <div class="flex flex-col">
    <PageHeader title="End-user access"
                description="Who may read and write this application's data: the grants in the application's own store, on the whole application, on one tenant, on one definition's rows, or on those rows in one tenant.">
      <template #actions>
        <Button label="Users" icon="pi pi-arrow-left" size="small" severity="secondary" text @click="router.push({ name: 'application-users', params: { applicationId } })" />
      </template>
    </PageHeader>

    <NoAccessState v-if="refused" description="No grant you hold reaches this application's access. A member who manages the application's access can grant you access from its Access page." />
    <div v-else class="flex flex-col gap-4">
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

      <DashboardSection :icon="KeyRound" :tint="TINTS.blue" :title="whereTitle" :count="loading ? undefined : shown.length"
                        :description="subjectFilter ? `The grants of ${labelOf(subjectFilter)} here.` : whereDescription">
        <template #actions>
          <Select v-model="where" :options="whereOptions" option-label="label" option-value="id" editable size="small"
                  placeholder="Whole application or a tenant id" class="w-64" />
          <Select v-model="definition" :options="definitionOptions" option-label="label" option-value="id" size="small"
                  placeholder="Every definition or one" class="w-56" />
          <Button v-if="subjectFilter" label="Show everyone" icon="pi pi-filter-slash" size="small" severity="secondary" outlined @click="clearSubject" />
          <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDialogVisible = true" />
        </template>
        <div v-if="loading" class="flex flex-col gap-2 p-5">
          <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
        </div>
        <EmptyChartCharacter v-else-if="shown.length === 0" class="py-6" :title="subjectFilter ? 'No grants for them yet' : 'No grants here yet'"
                             :hint="canManageAccess ? 'Grant a user or a machine a role to let them read or write the application\'s data here.' : 'Access to the application\'s data comes from grants made here.'" />
        <DataTable v-else :value="shown">
          <Column header="Who" style="width: 35%">
            <template #body="{ data, index }">
              <SubjectCell :subject="data.subject" :label="labelOf(data.subject)" :index="index" />
            </template>
          </Column>
          <Column header="Role" style="width: 30%">
            <template #body="{ data }"><RoleCell :role="roleOf(data.roleId)" :role-id="data.roleId" /></template>
          </Column>
          <Column header="Where" style="width: 20%">
            <template #body="{ data }">
              <TableChip :icon="MapPin">{{ resourceLabel(data.resource, definitions) }}</TableChip>
            </template>
          </Column>
          <Column style="width: 15%">
            <template #body="{ data }">
              <div class="flex justify-end">
                <Button v-if="canManageAccess" label="Revoke" icon="pi pi-times" size="small" severity="danger" text @click="confirmRevoke(data)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </DashboardSection>

      <EndUserAccessCheckPanel v-if="!loading" :tint="TINTS.blue" :application-id="applicationId" :users="members" :machines="machines" :roles="roles"
                               :catalog="catalog" :tenants="tenants" :definitions="definitions" :label-of="labelOf" :initial-subject-id="subjectFilter?.id"
                               :initial-resource="resource" />

      <EndUserGrantDialog v-model:visible="grantDialogVisible" :application-id="applicationId" :roles="roles" :users="members" :machines="machines"
                          :tenants="tenants" :definitions="definitions" :initial-resource="resource" @granted="onGranted" />
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
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { KeyRound, MapPin } from '@lucide/vue'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { type Grant, type Resource, type RoleDefinition, type Subject, SubjectKind } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, NoAccessState, PageHeader, RoleCell, SubjectCell, TINTS, TableChip,
         errorMessage, isAuthorizationError, showErrorToast } from '@kinotic-ai/frontend-common'
import EndUserAccessCheckPanel from '@/components/access/EndUserAccessCheckPanel.vue'
import EndUserGrantDialog from '@/components/access/EndUserGrantDialog.vue'
import { type DefinitionOption, resourceLabel, resourceOf, reaches, tenantOf } from '@/components/access/applicationResources'
import { useSubjects } from '@/components/access/useSubjects'

/**
 * The end-user access page of one application: the grants in the application's own store that reach the whole
 * application, one tenant, one definition's rows in every tenant or those rows in one tenant, a grant of a role
 * to one of the application's users or machines, and a check that explains anyone's access. Opened for one user
 * from the Users page, it shows that user's grants alone.
 */
const props = defineProps<{
  applicationId: string
}>()

const WHOLE_APPLICATION = ''
const EVERY_DEFINITION = ''
// the pickers list the first page of the application's published definitions, which covers an application of
// a few hundred; a definition beyond them is shown by its id
const DEFINITION_PAGE_SIZE = 500

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const { members, machines, load: loadSubjects, labelOf } = useSubjects(props.applicationId)

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const definitions = ref<DefinitionOption[]>([])
const canManageAccess = ref(false)
const loading = ref(true)
const refused = ref(false)
const error = ref<string | null>(null)
const grantDialogVisible = ref(false)
const where = ref<string>(typeof route.query.tenant === 'string' ? route.query.tenant : WHOLE_APPLICATION)
const definition = ref<string>(EVERY_DEFINITION)

/** The subject the page was opened for, from its query, or null for everyone's grants. */
const subjectFilter = computed<Subject | null>(() => {
  const id = route.query.subject
  return typeof id === 'string' && id.length > 0 ? { kind: SubjectKind.USER, id } : null
})

/** The resource the page is looking at: the whole application, the tenant typed or picked, the definition picked, or both. */
const resource = computed<Resource>(() => resourceOf(props.applicationId, where.value.trim(), definition.value))

const shown = computed(() => subjectFilter.value
    ? grants.value.filter(grant => grant.subject.id === subjectFilter.value?.id)
    : grants.value)

const whereTitle = computed(() => resource.value.type === 'application'
    ? 'Grants on the whole application'
    : `Grants reaching ${resourceLabel(resource.value, definitions.value)}`)
const whereDescription = computed(() => {
  let ret: string
  if (resource.value.type === 'tenant') {
    ret = 'Who holds a role on this tenant, and who holds one on the whole application, which reaches it too.'
  } else if (resource.value.type === 'entity_definition') {
    ret = 'Who holds a role on these rows in every tenant, and who holds one on the whole application, which reaches them too.'
  } else if (resource.value.type === 'tenant_definition') {
    ret = 'Who holds a role on these rows alone, and who holds one on the tenant, on the definition or on the whole application, which reach them too.'
  } else {
    ret = 'Who holds a role on the application, and so on the rows of every definition in every tenant.'
  }
  return ret
})

/** The tenants seen so far: those the application's users belong to and those a grant was made on. */
const tenants = computed(() => {
  const ids = new Set<string>()
  for (const option of members.value) {
    if (option.tenantId) {
      ids.add(option.tenantId)
    }
  }
  for (const grant of grants.value) {
    const tenant = tenantOf(grant.resource)
    if (tenant) {
      ids.add(tenant)
    }
  }
  return [...ids].sort()
})

const whereOptions = computed(() => [
  { label: 'Whole application', id: WHOLE_APPLICATION },
  ...tenants.value.map(id => ({ label: `Tenant ${id}`, id }))
])

const definitionOptions = computed(() => [
  { label: 'Every definition', id: EVERY_DEFINITION },
  ...definitions.value.map(option => ({ label: option.name, id: option.id }))
])

/** The permissions the application's store defines, read from the roles that bundle them. */
const catalog = computed(() => {
  const names = new Set<string>()
  for (const role of roles.value) {
    for (const permission of role.permissions) {
      names.add(permission)
    }
  }
  return [...names].sort()
})

watch(() => [props.applicationId, resource.value.type, resource.value.id], load, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const [found, defined, managed, published] = await Promise.all([
      Kinotic.applicationAccess.findGrants(props.applicationId, resource.value),
      Kinotic.applicationAccess.findRoles(props.applicationId),
      Kinotic.permissions.listAccessible('application', 'can_manage_access'),
      Kinotic.entityDefinitions.findAllPublishedForApplication(props.applicationId, Pageable.create(0, DEFINITION_PAGE_SIZE)),
      loadSubjects()
    ])
    grants.value = found
    roles.value = defined
    definitions.value = (published.content ?? []).flatMap(entity => entity.id ? [{ id: entity.id, name: entity.name }] : [])
    canManageAccess.value = managed.includes(props.applicationId)
    refused.value = false
  } catch (err) {
    refused.value = isAuthorizationError(err)
    error.value = refused.value ? null : errorMessage(err, 'Failed to load the application\'s end-user access')
  } finally {
    loading.value = false
  }
}

function roleOf(roleId: string): RoleDefinition | undefined {
  return roles.value.find(role => role.id === roleId)
}

function clearSubject(): void {
  router.replace({ name: 'application-users-access', params: { applicationId: props.applicationId } })
}

function onGranted(grant: Grant): void {
  if (reaches(grant.resource, resource.value)) {
    grants.value = [...grants.value, grant]
  }
}

function confirmRevoke(grant: Grant): void {
  confirm.require({
    header: 'Revoke access',
    message: `Revoke ${roleOf(grant.roleId)?.name ?? grant.roleId} from ${labelOf.value(grant.subject)} on ${resourceLabel(grant.resource, definitions.value)}? They keep whatever other grants reach them.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.applicationAccess.revoke(props.applicationId, grant.resource, grant.id)
        grants.value = grants.value.filter(other => other.id !== grant.id)
        toast.add({ severity: 'success', summary: 'Access revoked', life: 4000 })
      } catch (err) {
        showErrorToast(toast, 'Failed to revoke access', err, { life: 8000 })
      }
    }
  })
}
</script>
