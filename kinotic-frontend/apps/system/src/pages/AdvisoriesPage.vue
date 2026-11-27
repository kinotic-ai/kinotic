<template>
  <div>
    <PageHeader title="Advisories"
                description="The vulnerability advisories read from OSV: how far each ecosystem has been read, and what an advisory affects across the organizations and the cluster.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="statusLoading" @click="refresh" />
      </template>
    </PageHeader>

    <div class="flex flex-col gap-4">
      <Message v-if="statusError" severity="error" :closable="false">{{ statusError }}</Message>
      <div class="grid gap-4 sm:grid-cols-2">
        <StatCard v-for="stat in importStats" :key="stat.label" :icon="DatabaseZap" :tint="stat.tint" :label="stat.label"
                  :value="stat.value" :detail="stat.detail" :loading="statusLoading && statuses === null" :to="stat.to" />
      </div>

      <DashboardSection :icon="Search" :tint="TINTS.sky" title="Look up an advisory"
                        :description="listing?.ecosystem
                          ? `The ${ECOSYSTEM_NAMES[listing.ecosystem]} advisories imported into Kinotic and not withdrawn, the most recently changed first. Select one to see the projects and server builds it affects.`
                          : 'Find an advisory by its OSV id, an alias such as its CVE, or a package URL, and see the projects and server builds it affects.'"
                        :count="listing?.ecosystem && found ? foundTotal : undefined">
        <form class="flex flex-wrap items-center gap-2 border-b border-surface-200 px-5 py-3 dark:border-surface-700" @submit.prevent="lookUp">
          <IconField class="w-full sm:w-[28rem]">
            <InputIcon class="pi pi-search" />
            <InputText v-model="searchText" placeholder="CVE-2021-44228, GHSA-jfh8-c2jp-5v3q or pkg:npm/lodash" size="small" class="w-full font-mono" />
          </IconField>
          <Button type="submit" label="Look up" size="small" :disabled="!searchText.trim()" :loading="searching" />
        </form>

        <Message v-if="searchError" severity="error" :closable="false" class="m-5">{{ searchError }}</Message>
        <p v-else-if="listing === null" class="px-5 py-4 text-sm text-muted-color">
          Search by id or alias to find an advisory even after it was withdrawn; a package URL lists the advisories standing against any version of the package. Select an ecosystem's card above to list the advisories imported for it.
        </p>
        <template v-else-if="found !== null">
          <EmptyChartCharacter v-if="foundTotal === 0 && listing.ecosystem" class="py-6" title="No advisory imported"
                               :hint="`No ${ECOSYSTEM_NAMES[listing.ecosystem]} advisory has been imported from OSV yet.`" />
          <EmptyChartCharacter v-else-if="foundTotal === 0" class="py-6" title="No advisory found"
                               :hint="`No stored advisory is known as ${listing.search}. An id or alias must match in full.`" />
          <template v-else-if="foundTotal > 1 || listing.ecosystem">
            <DataTable :value="found.content" size="small" class="text-sm" data-key="id" selection-mode="single"
                       :selection="selected" @row-select="select($event.data)">
              <Column header="Severity" style="width: 9rem">
                <template #body="{ data }"><Tag :value="labelOf(data)" :severity="advisorySeverityTag(data.severity)" /></template>
              </Column>
              <Column header="Advisory" style="width: 30%">
                <template #body="{ data }">
                  <span class="font-mono text-sm font-medium text-surface-950 dark:text-surface-0">{{ data.id }}</span>
                  <div v-if="data.aliases.length" class="font-mono text-xs text-muted-color">{{ data.aliases.join(', ') }}</div>
                </template>
              </Column>
              <Column header="Summary">
                <template #body="{ data }">{{ data.summary }}</template>
              </Column>
              <Column header="Modified" style="width: 10rem">
                <template #body="{ data }"><TimePill :date="data.modified" /></template>
              </Column>
            </DataTable>
            <p v-if="foundTotal > DEEPEST_PAGED" class="border-t border-surface-200 px-5 py-3 text-sm text-muted-color dark:border-surface-700">
              The first {{ DEEPEST_PAGED.toLocaleString() }} of {{ foundTotal.toLocaleString() }} advisories are listed; look one up by id, alias or package to find any of them.
            </p>
            <TablePaginator class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                            :first="foundFirst" :rows="foundPageSize" :total-records="Math.min(foundTotal, DEEPEST_PAGED)"
                            :rows-per-page-options="PAGE_SIZES" @page="onFoundPage" />
          </template>
        </template>
      </DashboardSection>

      <template v-if="selected">
        <DashboardSection :icon="ShieldAlert" :tint="selected.withdrawn ? TINTS.ink : TINTS.red" :title="selected.id">
          <template #default>
            <div class="flex flex-col gap-4 p-5">
              <div class="flex flex-wrap items-center gap-2">
                <Tag :value="labelOf(selected)" :severity="advisorySeverityTag(selected.severity)" />
                <Tag v-if="selected.withdrawn" value="Withdrawn" severity="secondary" />
                <span v-for="alias in selected.aliases" :key="alias"
                      class="rounded-md bg-surface-100 px-1.5 py-0.5 font-mono text-xs text-surface-700 dark:bg-surface-800 dark:text-surface-200">{{ alias }}</span>
                <a :href="advisoryUrl(selected.id)" target="_blank" rel="noopener" class="ml-auto text-sm text-primary hover:underline">
                  Open on osv.dev <i class="pi pi-external-link text-xs" />
                </a>
              </div>
              <p v-if="selected.summary" class="text-surface-800 dark:text-surface-100">{{ selected.summary }}</p>
              <div class="flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-color">
                <span>Modified <TimePill :date="selected.modified" /></span>
                <span v-if="selected.withdrawn">Withdrawn <TimePill :date="selected.withdrawn" /></span>
              </div>
              <div>
                <div class="mb-1 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Affected packages</div>
                <ul class="flex flex-col gap-1.5">
                  <li v-for="(affected, index) in selected.affected" :key="index" class="text-xs">
                    <span class="font-mono font-medium text-surface-950 dark:text-surface-0">{{ packageOf(affected.purl).name }}</span>
                    <span class="text-muted-color"> · {{ affected.ecosystem }}</span>
                    <div v-for="(range, rangeIndex) in affected.ranges" :key="rangeIndex" class="font-mono text-muted-color">{{ rangeText(range) }}</div>
                    <div v-if="affected.versions.length" class="font-mono text-muted-color break-all">
                      versions {{ affected.versions.slice(0, VERSIONS_SHOWN).join(', ') }}<span v-if="affected.versions.length > VERSIONS_SHOWN"> and {{ affected.versions.length - VERSIONS_SHOWN }} more</span>
                    </div>
                  </li>
                </ul>
              </div>
            </div>
          </template>
        </DashboardSection>

        <DashboardSection :icon="Server" :tint="TINTS.blue" title="Server builds" :count="affectedBuilds?.length"
                          description="The running server builds whose libraries the advisory affects.">
          <Message v-if="buildsError" severity="warn" :closable="false" class="m-5">{{ buildsError }}</Message>
          <div v-else-if="affectedBuilds === null" class="py-6 text-center text-sm text-muted-color">Checking the servers' libraries…</div>
          <p v-else-if="affectedBuilds.length === 0" class="px-5 py-4 text-sm text-muted-color">No running server build is affected.</p>
          <ul v-else class="flex flex-col divide-y divide-surface-200 dark:divide-surface-700">
            <li v-for="(build, index) in affectedBuilds" :key="index" class="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-5 py-3 text-sm">
              <span class="font-semibold text-surface-950 dark:text-surface-0">{{ build.serverName || UNKNOWN_VERSION }}</span>
              <span class="font-mono text-surface-700 dark:text-surface-200">{{ releaseOf(build.version) }}</span>
              <span class="text-muted-color">{{ build.nodeCount }} {{ build.nodeCount === 1 ? 'node' : 'nodes' }}</span>
              <span class="basis-full">
                <VulnerablePackageList :packages="build.packages" />
              </span>
            </li>
          </ul>
        </DashboardSection>

        <DashboardSection :icon="FolderGit2" :tint="TINTS.purple" title="Projects" :count="alerts ? alertsTotal : undefined"
                          description="The projects of every organization whose SBOM holds a version the advisory affects, one alert each.">
          <Message v-if="alertsError" severity="error" :closable="false" class="m-5">{{ alertsError }}</Message>
          <template v-else>
            <DataTable :value="alerts?.content ?? []" size="small" class="text-sm" data-key="id">
              <template #empty>
                <div v-if="alerts === null" class="py-6 text-center text-sm text-muted-color">Loading projects…</div>
                <EmptyChartCharacter v-else class="py-6" title="No project affected"
                                     :hint="selected.withdrawn ? 'A withdrawn advisory raises no alerts.' : 'No project SBOM holds a version the advisory affects.'" />
              </template>
              <Column header="Organization" style="width: 20%">
                <template #body="{ data }">
                  <RouterLink :to="`/organizations/${data.organizationId}`" class="font-mono text-primary hover:underline">{{ data.organizationId }}</RouterLink>
                </template>
              </Column>
              <Column header="Project" style="width: 28%">
                <template #body="{ data }">
                  <RouterLink :to="`/organizations/${data.organizationId}/applications/${data.applicationId}/project/${data.projectId}`"
                              class="font-mono text-primary hover:underline">{{ data.projectId }}</RouterLink>
                  <div class="font-mono text-xs text-muted-color">{{ data.applicationId }}</div>
                </template>
              </Column>
              <Column header="Affected versions">
                <template #body="{ data }"><VulnerablePackageList :packages="data.finding.packages" /></template>
              </Column>
              <Column header="Raised" style="width: 9rem">
                <template #body="{ data }"><TimePill :date="data.created" /></template>
              </Column>
              <Column style="width: 9rem">
                <template #body="{ data }"><Tag v-if="data.acknowledged" value="Acknowledged" severity="secondary" /></template>
              </Column>
            </DataTable>
            <p v-if="alertsTotal > DEEPEST_PAGED" class="border-t border-surface-200 px-5 py-3 text-sm text-muted-color dark:border-surface-700">
              The first {{ DEEPEST_PAGED.toLocaleString() }} of {{ alertsTotal.toLocaleString() }} affected projects are listed.
            </p>
            <TablePaginator v-if="alertsTotal" class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                            :first="alertsFirst" :rows="alertsPageSize" :total-records="Math.min(alertsTotal, DEEPEST_PAGED)"
                            :rows-per-page-options="PAGE_SIZES" @page="onAlertsPage" />
          </template>
        </DashboardSection>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { DatabaseZap, FolderGit2, Search, Server, ShieldAlert } from '@lucide/vue'

import { Direction, Kinotic, Order, Pageable, Sort, type Page } from '@kinotic-ai/core'
import { AdvisoryType, PackageEcosystem, type AffectedRange, type VulnerabilityAdvisory, type VulnerabilityAlert, type VulnerablePackage } from '@kinotic-ai/management-api'
import type { OsvImportStatus } from '@kinotic-ai/system-api'
import { DashboardSection, EmptyChartCharacter, PageHeader, StatCard, TablePaginator, TimePill, TINTS, errorMessage, usePaging } from '@kinotic-ai/frontend-common'
import { advisorySeverityTag, advisoryUrl, packageOf, VulnerablePackageList } from '@kinotic-ai/frontend-common/management'

const route = useRoute()
const router = useRouter()

const PAGE_SIZES = [10, 25, 50]
// how deep into its results a search reads, which every page size above divides, so the last page ends on it
const DEEPEST_PAGED = 10_000

/** How many of an affected package's listed versions show before the rest are counted. */
const VERSIONS_SHOWN = 12

/** What a node running from classes, with no packaged version, shows for one. */
const UNKNOWN_VERSION = 'unknown'

const ECOSYSTEM_NAMES: Record<PackageEcosystem, string> = {
  [PackageEcosystem.NPM]: 'npm',
  [PackageEcosystem.MAVEN]: 'Maven',
}

/** Advisories found or listed, the most recently changed first; the id orders those changed together. */
const ADVISORY_SORT = new Sort()
ADVISORY_SORT.orders = [new Order('modified', Direction.DESC), new Order('id', Direction.ASC)]

/** Alerts across organizations, grouped by organization. */
const ALERT_SORT = new Sort()
ALERT_SORT.orders = [new Order('organizationId', Direction.ASC), new Order('id', Direction.ASC)]

// ---- import status ----

const statuses = ref<OsvImportStatus[] | null>(null)
const statusLoading = ref(false)
const statusError = ref<string | null>(null)

const importStats = computed(() => (statuses.value ?? Object.values(PackageEcosystem).map(ecosystem => ({ ecosystem, syncedThrough: null, advisoryCount: 0 })))
  .map(status => ({
    label: `${ECOSYSTEM_NAMES[status.ecosystem]} advisories`,
    value: statuses.value ? status.advisoryCount.toLocaleString() : '—',
    detail: status.syncedThrough ? `Read through ${new Date(status.syncedThrough).toLocaleString()}` : 'Not imported yet',
    tint: statuses.value && !status.syncedThrough ? TINTS.orange : TINTS.blue,
    to: { query: { ecosystem: status.ecosystem } },
  })))

async function loadStatus(): Promise<void> {
  statusLoading.value = true
  try {
    statuses.value = await Kinotic.vulnerabilityAdvisories.findImportStatus()
    statusError.value = null
  } catch (err) {
    statusError.value = errorMessage(err, 'The import status could not be loaded')
  } finally {
    statusLoading.value = false
  }
}

// ---- lookup and ecosystem listing ----

type AdvisoryListing = { search: string, ecosystem?: undefined } | { search?: undefined, ecosystem: PackageEcosystem }

const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')

/**
 * What the results list, kept in the route so it can be linked to: the advisories a search finds,
 * or the advisories imported for an ecosystem.
 */
const listing = computed<AdvisoryListing | null>(() => {
  const { q, ecosystem } = route.query
  let ret: AdvisoryListing | null = null
  if (typeof q === 'string' && q.trim()) {
    ret = { search: q.trim() }
  } else if (Object.values(PackageEcosystem).includes(ecosystem as PackageEcosystem)) {
    ret = { ecosystem: ecosystem as PackageEcosystem }
  }
  return ret
})
// a listing is replaced only when what it lists changes, not on every route update
const listingKey = computed(() => JSON.stringify(listing.value))

const found = ref<Page<VulnerabilityAdvisory> | null>(null)
const searching = ref(false)
const searchError = ref<string | null>(null)
const selected = ref<VulnerabilityAdvisory | null>(null)
const foundTotal = computed(() => found.value?.totalElements ?? 0)

const { first: foundFirst, pageSize: foundPageSize, page: foundPage, onPage: onFoundPage } = usePaging(PAGE_SIZES[0], () => { void loadFound() })

function lookUp(): void {
  const q = searchText.value.trim()
  if (q === listing.value?.search) {
    void loadFound()
  } else {
    const { ecosystem: _listed, ...query } = route.query
    void router.replace({ query: { ...query, q } })
  }
}

// The search started last; a newer one replaces the results of one still under way
let latestSearch = 0

async function loadFound(): Promise<void> {
  const shown = listing.value
  const current = ++latestSearch
  found.value = null
  if (shown === null) {
    selected.value = null
    return
  }
  searching.value = true
  try {
    const pageable = Pageable.create(foundPage.value, foundPageSize.value, ADVISORY_SORT)
    const page = shown.ecosystem
                 ? await Kinotic.vulnerabilityAdvisories.findAdvisoriesOf(shown.ecosystem, pageable)
                 : await Kinotic.vulnerabilityAdvisories.findAdvisories(shown.search, pageable)
    if (current === latestSearch) {
      found.value = page
      searchError.value = null
      const content = page.content ?? []
      // a search's single match opens at once; a list reopens the advisory opened before, as it reads now
      const reopened = shown.search && content.length === 1 && page.totalElements === 1
                       ? content[0]
                       : content.find(advisory => advisory.id === selected.value?.id)
      if (reopened) {
        select(reopened)
      } else {
        selected.value = null
      }
    }
  } catch (err) {
    if (current === latestSearch) {
      searchError.value = errorMessage(err, shown.ecosystem ? 'The advisories could not be listed' : 'The advisories could not be looked up')
      selected.value = null
    }
  } finally {
    if (current === latestSearch) {
      searching.value = false
    }
  }
}

watch(listingKey, () => {
  searchText.value = listing.value?.search ?? ''
  foundFirst.value = 0
  void loadFound()
})

/** What an advisory's tag reads: Malicious for a malicious package, its severity otherwise. */
function labelOf(advisory: VulnerabilityAdvisory): string {
  return advisory.type === AdvisoryType.MALICIOUS ? 'Malicious' : advisory.severity
}

/** A range's events as written by OSV, e.g. "introduced 4.0.0 · fixed 4.4.2". */
function rangeText(range: AffectedRange): string {
  return range.events.map(event => {
    let ret: string
    if (event.introduced !== null) {
      ret = event.introduced === '0' ? 'introduced in the first version' : `introduced ${event.introduced}`
    } else if (event.fixed !== null) {
      ret = `fixed ${event.fixed}`
    } else if (event.lastAffected !== null) {
      ret = `last affected ${event.lastAffected}`
    } else {
      ret = `below ${event.limit}`
    }
    return ret
  }).join(' · ')
}

// ---- what the selected advisory affects ----

const alerts = ref<Page<VulnerabilityAlert> | null>(null)
const alertsError = ref<string | null>(null)
const alertsTotal = computed(() => alerts.value?.totalElements ?? 0)
const { first: alertsFirst, pageSize: alertsPageSize, page: alertsPage, onPage: onAlertsPage } = usePaging(PAGE_SIZES[0], () => { void loadAlerts() })

/** Each running server build the selected advisory affects, with its affected libraries; null while they are checked. */
const affectedBuilds = ref<Array<{ serverName: string | null, version: string | null, nodeCount: number, packages: VulnerablePackage[] }> | null>(null)
const buildsError = ref<string | null>(null)

function select(advisory: VulnerabilityAdvisory): void {
  selected.value = advisory
  alertsFirst.value = 0
  void loadAlerts()
  void loadBuilds()
}

// The advisory whose projects and builds were asked for last, so an earlier answer arriving late is dropped
let latestAlerts = 0
let latestBuilds = 0

async function loadAlerts(): Promise<void> {
  const advisory = selected.value
  const current = ++latestAlerts
  alerts.value = null
  if (!advisory) {
    return
  }
  try {
    const page = await Kinotic.vulnerabilityAdvisories.findAlerts(advisory.id, Pageable.create(alertsPage.value, alertsPageSize.value, ALERT_SORT))
    if (current === latestAlerts) {
      alerts.value = page
      alertsError.value = null
    }
  } catch (err) {
    if (current === latestAlerts) {
      alertsError.value = errorMessage(err, 'The affected projects could not be loaded')
    }
  }
}

async function loadBuilds(): Promise<void> {
  const advisory = selected.value
  const current = ++latestBuilds
  affectedBuilds.value = null
  if (!advisory) {
    return
  }
  try {
    const servers = await Kinotic.clusterVulnerabilities.findAll()
    if (current === latestBuilds) {
      affectedBuilds.value = servers.flatMap(server => server.findings
                                                            .filter(finding => finding.advisoryId === advisory.id)
                                                            .map(finding => ({ serverName: server.serverName,
                                                                               version: server.version,
                                                                               nodeCount: server.nodeCount,
                                                                               packages: finding.packages })))
      buildsError.value = null
    }
  } catch (err) {
    if (current === latestBuilds) {
      buildsError.value = errorMessage(err, "The servers' libraries could not be checked")
    }
  }
}

/** The release a version string names, without its build suffix: "2.18.0" of "2.18.0#20260423-sha1:d49adada". */
function releaseOf(version: string | null): string {
  return version?.split('#')[0] || UNKNOWN_VERSION
}

function refresh(): void {
  void loadStatus()
  void loadFound()
}

onMounted(() => {
  void loadStatus()
  void loadFound()
})
</script>
