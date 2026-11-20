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
          <Button v-if="canManageAccess" label="Grant access" icon="pi pi-plus" size="small" @click="grantDrawerVisible = true" />
        </template>
        <div v-if="loading" class="flex flex-col gap-2 p-5">
          <Skeleton v-for="n in 2" :key="n" height="2.5rem" />
        </div>
        <EmptyChartCharacter v-else-if="shown.length === 0" class="py-6" :title="subjectFilter ? 'No grants for them yet' : 'No grants here yet'"
                             :hint="canManageAccess ? 'Grant a user, a machine or everyone in a tenant a role to let them read or write the application\'s data here.' : 'Access to the application\'s data comes from grants made here.'" />
        <GrantsTable v-else :grants="shown" :roles="roles" :label-of="labelOf" :detail-of="detailOf" where-header="Where"
                     :revoke="canManageAccess ? revoke : undefined" :revoke-where="grant => `on ${resourceLabel(grant.resource, definitions)}`"
                     revoke-consequence="They keep whatever other grants reach them." @revoked="onRevoked">
          <template #where="{ grant }">
            <TableChip :icon="MapPin">{{ resourceLabel(grant.resource, definitions) }}</TableChip>
          </template>
        </GrantsTable>
      </DashboardSection>

      <EndUserAccessCheckPanel v-if="contextLoaded" :tint="TINTS.blue" :application-id="applicationId" :users="members" :search-users="searchMembers" :machines="machines" :roles="roles"
                               :catalog="catalog" :tenants="tenants" :definitions="definitions" :label-of="labelOf" :initial-subject-id="subjectFilter?.id"
                               :initial-resource="resource" />

      <EndUserGrantDrawer v-model:visible="grantDrawerVisible" :application-id="applicationId" :roles="roles" :users="members" :search-users="searchMembers" :machines="machines"
                          :tenants="tenants" :definitions="definitions" :initial-resource="resource" @granted="onGranted" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Skeleton from 'primevue/skeleton'
import { KeyRound, MapPin } from '@lucide/vue'
import { Kinotic, type Page, Pageable } from '@kinotic-ai/core'
import { type Grant, type Resource, type RoleDefinition, type Subject, SubjectType } from '@kinotic-ai/management-api'
import { DashboardSection, EmptyChartCharacter, NoAccessState, PageHeader, TINTS, TableChip,
         errorMessage, isAuthorizationError, latestOnly, permissionsOf } from '@kinotic-ai/frontend-common'
import { GrantsTable } from '@kinotic-ai/frontend-common/management'
import EndUserAccessCheckPanel from '@/components/access/EndUserAccessCheckPanel.vue'
import EndUserGrantDrawer from '@/components/access/EndUserGrantDrawer.vue'
import { type DefinitionOption, EVERY_DEFINITION, WHOLE_APPLICATION, definitionChoices, resourceLabel, resourceOf, reaches, tenantOf,
         tenantOption, whereChoices } from '@/components/access/applicationResources'
import { useSubjects } from '@/components/access/useSubjects'

/**
 * The end-user access page of one application: the grants in the application's own store that reach the whole
 * application, one tenant, one definition's rows in every tenant or those rows in one tenant, a grant of a role
 * to one of the application's users or machines or to every user of a tenant, and a check that explains anyone's access. Opened for one user
 * from the Users page, it shows that user's grants alone.
 */
const props = defineProps<{
  applicationId: string
}>()

// the pickers list the first page of the application's published definitions and of its tenants, which covers an
// application of a few hundred of each; a definition beyond them is shown by its id, and a tenant's id can be typed
const PICKER_PAGE_SIZE = 500

const route = useRoute()
const router = useRouter()
const { members, machines, load: loadSubjects, searchMembers, labelOf: identityLabelOf } = useSubjects(() => props.applicationId)

/** The name of a grant's subject: a user or a machine as the pickers name it, a tenant as everyone in it. */
const labelOf = computed(() => (subject: Subject) => subject.type === SubjectType.TENANT
    ? tenantOption(subject.id).label
    : identityLabelOf.value(subject))

const grants = ref<Grant[]>([])
const roles = ref<RoleDefinition[]>([])
const definitions = ref<DefinitionOption[]>([])
const tenantRecords = ref<string[]>([])
const canManageAccess = ref(false)
const loading = ref(true)
// whether the application's roles, definitions, tenants and subjects are read, which the check panel is built on
const contextLoaded = ref(false)
// Each load keeps its own failure, so a load of the grants alone neither clears nor hides what the context's load
// reported: the page is refused when either was, and otherwise shows the first failure
const contextFailure = ref<unknown>(null)
const grantsFailure = ref<unknown>(null)
const grantDrawerVisible = ref(false)
const where = ref<string>(typeof route.query.tenant === 'string' ? route.query.tenant : WHOLE_APPLICATION)
const definition = ref<string>(EVERY_DEFINITION)

const failures = computed(() => [contextFailure.value, grantsFailure.value].filter(failure => failure !== null))
const refused = computed(() => failures.value.some(isAuthorizationError))
const error = computed(() => {
  const other = failures.value.find(failure => !isAuthorizationError(failure))
  return other === undefined ? null : errorMessage(other, 'Failed to load the application\'s end-user access')
})

/** The subject the page was opened for, from its query, or null for everyone's grants. */
const subjectFilter = computed<Subject | null>(() => {
  const id = route.query.subject
  return typeof id === 'string' && id.length > 0 ? { type: SubjectType.USER, id } : null
})

/** The resource the page is looking at: the whole application, the tenant typed or picked, the definition picked, or both. */
const resource = computed<Resource>(() => resourceOf(props.applicationId, where.value.trim(), definition.value))

const shown = computed(() => subjectFilter.value
    ? grants.value.filter(grant => grant.subject.type === subjectFilter.value?.type && grant.subject.id === subjectFilter.value?.id)
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

/** The tenants known so far: those the application's administrators created and those a grant was made on. */
const tenants = computed(() => {
  const ids = new Set<string>(tenantRecords.value)
  for (const grant of grants.value) {
    const tenant = tenantOf(grant.resource)
    if (tenant) {
      ids.add(tenant)
    }
  }
  return [...ids].sort()
})

const whereOptions = computed(() => whereChoices(tenants.value))
const definitionOptions = computed(() => definitionChoices(definitions.value))

/** The permissions the application's store defines, read from the roles that bundle them. */
const catalog = computed(() => permissionsOf(roles.value))

// The page is kept when the header switches the application in place, and the tenant picker is editable, so a
// keystroke starts a load of the grants while an earlier one is in flight; only the latest load's answer, or
// failure, reaches the page. The application's context is read apart from its grants, so a keystroke reads the
// grants alone and the check panel keeps what was chosen in it
const startContextLoad = latestOnly()
const startGrantsLoad = latestOnly()

// the pickers name a tenant and a definition of the application the page showed, which the next one does not have;
// declared first, so the grants load once, for the reset resource
watch(() => props.applicationId, () => {
  where.value = WHOLE_APPLICATION
  definition.value = EVERY_DEFINITION
})
watch(() => props.applicationId, loadContext, { immediate: true })
watch(() => [props.applicationId, resource.value.type, resource.value.id], loadGrants, { immediate: true })

async function loadContext(): Promise<void> {
  const isLatest = startContextLoad()
  contextLoaded.value = false
  contextFailure.value = null
  try {
    const [defined, managed, published, created] = await Promise.all([
      Kinotic.applicationAccess.findRoles(props.applicationId),
      Kinotic.permissions.listAccessible('application', 'can_manage_access'),
      pickable(Kinotic.entityDefinitions.findAllPublishedForApplication(props.applicationId, Pageable.create(0, PICKER_PAGE_SIZE))),
      pickable(Kinotic.tenants.findAllForApplication(props.applicationId, Pageable.create(0, PICKER_PAGE_SIZE))),
      loadSubjects()
    ])
    if (!isLatest()) {
      return
    }
    roles.value = defined
    definitions.value = published.flatMap(entity => entity.id ? [{ id: entity.id, name: entity.name }] : [])
    tenantRecords.value = created.flatMap(tenant => tenant.id ? [tenant.id] : [])
    canManageAccess.value = managed.includes(props.applicationId)
    contextLoaded.value = true
  } catch (err) {
    if (isLatest()) {
      contextFailure.value = err
    }
  }
}

// A picker's list the caller may not read is left empty, as the subjects are: a caller who may read access but not
// the definitions or the tenants still sees the grants, with a definition named by its id and a tenant's id typed;
// any other failure fails the load
async function pickable<T>(page: Promise<Page<T>>): Promise<T[]> {
  let ret: T[] = []
  try {
    ret = (await page).content ?? []
  } catch (err) {
    if (!isAuthorizationError(err)) {
      throw err
    }
  }
  return ret
}

async function loadGrants(): Promise<void> {
  const isLatest = startGrantsLoad()
  loading.value = true
  grantsFailure.value = null
  try {
    const found = await Kinotic.applicationAccess.findGrants(props.applicationId, resource.value)
    if (!isLatest()) {
      return
    }
    grants.value = found
  } catch (err) {
    if (!isLatest()) {
      return
    }
    grantsFailure.value = err
  } finally {
    if (isLatest()) {
      loading.value = false
    }
  }
}

// a machine is a user subject to the store, so the row says which of the two it is
function detailOf(subject: Subject): string {
  let ret: string
  if (subject.type === SubjectType.TENANT) {
    ret = 'Every user of the tenant'
  } else {
    ret = machines.value.some(option => option.subject.id === subject.id) ? 'Machine' : 'User'
  }
  return ret
}

function clearSubject(): void {
  router.replace({ name: 'application-users-access', params: { applicationId: props.applicationId } })
}

function onGranted(grant: Grant): void {
  if (reaches(grant.resource, resource.value)) {
    grants.value = [...grants.value, grant]
  }
}

function revoke(grant: Grant): Promise<void> {
  return Kinotic.applicationAccess.revoke(props.applicationId, grant.resource, grant.id)
}

function onRevoked(grant: Grant): void {
  grants.value = grants.value.filter(other => other.id !== grant.id)
}
</script>
