<template>
  <div class="flex flex-col">
    <PageHeader title="SBOM"
                description="The project's software bill of materials: every package its bun.lock installs and how the project depends on it. The last step of a deployment reads it whenever the dependencies changed.">
      <template #actions>
        <Button v-if="dependencies" label="Download" icon="pi pi-download" severity="secondary" outlined @click="download" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="loading" class="p-6 text-sm text-muted-color">Loading SBOM…</div>

    <div v-else-if="!dependencies" class="flex flex-col items-center rounded-xl border border-dashed border-surface-300 px-6 py-14 text-center dark:border-surface-700">
      <span :class="['flex h-12 w-12 items-center justify-center rounded-xl', TINTS.sky]">
        <ListTree :size="24" :stroke-width="1.75" aria-hidden="true" />
      </span>
      <p class="mt-4 text-sm font-medium text-surface-950 dark:text-surface-0">No SBOM yet</p>
      <p class="mt-1 text-sm text-muted-color">The last step of a deployment reads it from the project's bun.lock whenever the dependencies change.</p>
    </div>

    <template v-else>
      <div class="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard :icon="GitCommitHorizontal" :tint="TINTS.green" label="Commit" detail="the last synced commit">
          <span class="font-mono text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0"
                v-tooltip.top="commitSha || undefined">{{ commitSha ? shortSha(commitSha) : '—' }}</span>
        </StatCard>
        <StatCard :icon="Package" :tint="TINTS.blue" label="Packages" :value="rows.length"
                  :detail="`${dependencies.direct.length} declared by the project`" />
        <StatCard :icon="Server" :tint="TINTS.orange" label="Runtime" :value="countOf(PackageScope.RUNTIME)"
                  detail="reached by production dependencies" />
        <StatCard :icon="Wrench" :tint="TINTS.purple" label="Development" :value="countOf(PackageScope.DEVELOPMENT)"
                  detail="only used to build and test" />
      </div>

      <DashboardSection :icon="ListTree" :tint="TINTS.sky" title="Packages" :count="visibleRows.length"
                        description="Search by name, or filter by how the project reaches each package. Download saves the SBOM as a CycloneDX 1.6 document, the dependency graph included.">
        <div class="flex flex-wrap items-center gap-3 border-b border-surface-200 px-5 py-3 dark:border-surface-700">
          <IconField class="w-full sm:w-80">
            <InputIcon class="pi pi-search" />
            <InputText v-model="search" placeholder="Search packages" size="small" class="w-full" />
          </IconField>
          <SelectButton v-model="scope" :options="SCOPE_OPTIONS" :allow-empty="false" size="small" />
        </div>

        <DataTable :value="pageRows" size="small" data-key="purl">
          <Column header="Package" style="width: 45%">
            <template #body="{ data }"><span class="font-mono text-sm break-all">{{ data.name }}</span></template>
          </Column>
          <Column header="Version" style="width: 20%">
            <template #body="{ data }"><span class="font-mono text-sm break-all">{{ data.version }}</span></template>
          </Column>
          <Column header="Scope" style="width: 15%">
            <template #body="{ data }">
              <Tag :value="data.scope" :severity="data.scope === PackageScope.RUNTIME ? 'info' : 'secondary'" />
            </template>
          </Column>
          <Column header="Dependency" style="width: 20%">
            <template #body="{ data }">
              <span :class="['text-sm', data.direct ? 'text-surface-950 dark:text-surface-0' : 'text-muted-color']">{{ data.direct ? 'Direct' : 'Transitive' }}</span>
            </template>
          </Column>
          <template #empty>
            <span class="text-sm text-muted-color">No package matches.</span>
          </template>
        </DataTable>

        <TablePaginator v-if="visibleRows.length" class="border-t border-surface-200 px-5 py-3 dark:border-surface-700"
                        :first="first" :rows="pageSize" :total-records="visibleRows.length"
                        :rows-per-page-options="PAGE_SIZES" @page="onPage" />
      </DashboardSection>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import type { PageState } from 'primevue/paginator'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { GitCommitHorizontal, ListTree, Package, Server, Wrench } from '@lucide/vue'
import { PageHeader, TablePaginator, errorMessage, shortSha } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import type { ProjectDependencies, ProjectDeployment } from '@kinotic-ai/management-api'
import DashboardSection from '@/components/DashboardSection.vue'
import StatCard from '@/components/StatCard.vue'
import { TINTS } from '@/util/tints'

/**
 * The project's SBOM, the one of the dependencies of the last synced commit: how many packages it
 * holds and how the project reaches them, then every package its bun.lock installs, searchable by
 * name and filtered by how the project uses it. Download saves it as a CycloneDX 1.6 document, the
 * dependency graph included.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

/** How the project uses a package. */
enum PackageScope {
  RUNTIME = 'Runtime',
  DEVELOPMENT = 'Development',
  OPTIONAL = 'Optional'
}

interface PackageRow {
  purl: string
  /** The package's name, with its scope */
  name: string
  version: string
  scope: PackageScope
  /** Whether one of the project's package.json files declares it */
  direct: boolean
}

const PAGE_SIZES = [25, 50, 100]
const ALL_SCOPES = 'All'
const SCOPE_OPTIONS = [ALL_SCOPES, PackageScope.RUNTIME, PackageScope.DEVELOPMENT, PackageScope.OPTIONAL]
const PURL_PREFIX = 'pkg:npm/'

const deployment = ref<ProjectDeployment | null>(null)
const dependencies = ref<ProjectDependencies | null>(null)
const search = ref('')
const scope = ref<string>(ALL_SCOPES)
const first = ref(0)
const pageSize = ref(50)
const loading = ref(true)
const error = ref<string | null>(null)

/** The last synced commit, whose dependencies the SBOM lists. */
const commitSha = computed(() => deployment.value?.artifacts?.commitSha ?? '')

const rows = computed<PackageRow[]>(() => {
  const tree = dependencies.value
  let ret: PackageRow[] = []
  if (tree) {
    const development = new Set(tree.development)
    const optional = new Set(tree.optional)
    const direct = new Set(tree.direct)
    ret = tree.packages
      .map((purl, position) => ({
        purl,
        ...nameAndVersionOf(purl),
        scope: development.has(position) ? PackageScope.DEVELOPMENT : optional.has(position) ? PackageScope.OPTIONAL : PackageScope.RUNTIME,
        direct: direct.has(position),
      }))
      .sort((a, b) => a.name.localeCompare(b.name))
  }
  return ret
})

const visibleRows = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return rows.value.filter(row => (scope.value === ALL_SCOPES || row.scope === scope.value)
    && (!needle || row.name.toLowerCase().includes(needle)))
})

const pageRows = computed(() => visibleRows.value.slice(first.value, first.value + pageSize.value))

// a new search or filter starts over at its first page
watch([search, scope], () => {
  first.value = 0
})

async function load(): Promise<void> {
  try {
    const [found, tree] = await Promise.all([Kinotic.projects.findDeployment(props.projectId),
                                             Kinotic.projects.findDependencies(props.projectId)])
    deployment.value = found
    dependencies.value = tree
  } catch (err) {
    error.value = errorMessage(err, 'The SBOM could not be loaded')
  } finally {
    loading.value = false
  }
}

function countOf(packageScope: PackageScope): number {
  return rows.value.filter(row => row.scope === packageScope).length
}

function onPage(state: PageState): void {
  first.value = state.first
  pageSize.value = state.rows
}

// pkg:npm/<name>@<version> with each part percent-encoded, so the one literal @ ends the name
function nameAndVersionOf(purl: string): { name: string, version: string } {
  const body = purl.slice(PURL_PREFIX.length)
  const at = body.indexOf('@')
  return {
    name: body.slice(0, at).split('/').map(decodeURIComponent).join('/'),
    version: decodeURIComponent(body.slice(at + 1)),
  }
}

// CycloneDX has no development scope: a development package is excluded, the scope of what is not
// reachable at runtime, and carries the npm taxonomy's development property. A package's bom-ref
// is its purl decoded, the form CycloneDX tools expect in the dependency graph's refs
function toCycloneDx(tree: ProjectDependencies, projectRows: PackageRow[]): object {
  const cycloneDxScopes: Record<PackageScope, string> = {
    [PackageScope.RUNTIME]: 'required',
    [PackageScope.OPTIONAL]: 'optional',
    [PackageScope.DEVELOPMENT]: 'excluded',
  }
  const bomRefs = new Map(projectRows.map(row => [row.purl, `${PURL_PREFIX}${row.name}@${row.version}`]))
  const bomRefOf = (position: number) => bomRefs.get(tree.packages[position]!)!
  const dependsOn = tree.packages.map((): string[] => [])
  for (const [dependent, dependency] of tree.edges) {
    dependsOn[dependent]!.push(bomRefOf(dependency))
  }
  return {
    bomFormat: 'CycloneDX',
    specVersion: '1.6',
    serialNumber: `urn:uuid:${crypto.randomUUID()}`,
    version: 1,
    metadata: {
      timestamp: new Date().toISOString(),
      component: { type: 'application', 'bom-ref': props.projectId, name: props.projectId },
    },
    components: projectRows.map(row => {
      const slash = row.name.startsWith('@') ? row.name.indexOf('/') : -1
      return {
        type: 'library',
        'bom-ref': bomRefs.get(row.purl),
        ...(slash === -1 ? { name: row.name } : { group: row.name.slice(0, slash), name: row.name.slice(slash + 1) }),
        version: row.version,
        purl: row.purl,
        scope: cycloneDxScopes[row.scope],
        ...(row.scope === PackageScope.DEVELOPMENT ? { properties: [{ name: 'cdx:npm:package:development', value: 'true' }] } : {}),
      }
    }),
    dependencies: [
      { ref: props.projectId, dependsOn: tree.direct.map(bomRefOf) },
      ...tree.packages.map((_, position) => ({ ref: bomRefOf(position), dependsOn: dependsOn[position]! })),
    ],
  }
}

function download(): void {
  if (dependencies.value !== null) {
    const bom = JSON.stringify(toCycloneDx(dependencies.value, rows.value), null, 2)
    const url = URL.createObjectURL(new Blob([bom], { type: 'application/vnd.cyclonedx+json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${props.projectId}-${shortSha(commitSha.value)}.cdx.json`
    link.click()
    URL.revokeObjectURL(url)
  }
}

void load()
</script>
